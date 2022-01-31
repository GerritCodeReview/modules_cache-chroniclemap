// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.googlesource.gerrit.modules.cache.chroniclemap;

import com.google.common.cache.AbstractLoadingCache;
import com.google.common.cache.CacheStats;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.cache.PersistentCache;
import com.google.gerrit.server.cache.PersistentCacheDef;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Injector;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.LongAdder;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;

public class ChronicleMapCacheImpl<K, V> extends AbstractLoadingCache<K, V>
    implements PersistentCache {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ChronicleMapCacheConfig config;
  private final ChronicleMapStore<K, V> store;
  private final LongAdder hitCount = new LongAdder();
  private final LongAdder missCount = new LongAdder();
  private final LongAdder loadSuccessCount = new LongAdder();
  private final LongAdder loadExceptionCount = new LongAdder();
  private final LongAdder totalLoadTime = new LongAdder();
  private final InMemoryLRU<K> hotEntries;
  private final PersistentCacheDef<K, V> cacheDefinition;
  private final ChronicleMapCacheLoader<K, V> memLoader;
  private final InMemoryCache<K, V> mem;

  ChronicleMapCacheImpl(
      PersistentCacheDef<K, V> def, ChronicleMapCacheConfig config, Injector injector)
      throws IOException {
    DisabledMetricMaker metricMaker = new DisabledMetricMaker();

    this.cacheDefinition = def;
    this.config = config;
    this.hotEntries =
        new InMemoryLRU<>(
            (int) Math.max(config.getMaxEntries() * config.getpercentageHotKeys() / 100, 1));
    this.store = createOrRecoverStore(def, config, metricMaker, injector);
    this.memLoader =
        new ChronicleMapCacheLoader<>(
            MoreExecutors.directExecutor(), store, config.getExpireAfterWrite());
    this.mem = memLoader.asInMemoryCacheBypass();

    ChronicleMapCacheMetrics metrics = new ChronicleMapCacheMetrics(metricMaker);
    metrics.registerCallBackMetrics(def.name(), this);
  }

  ChronicleMapCacheImpl(
      PersistentCacheDef<K, V> def,
      ChronicleMapCacheConfig config,
      MetricMaker metricMaker,
      ChronicleMapCacheLoader<K, V> memLoader,
      InMemoryCache<K, V> mem) {

    this.cacheDefinition = def;
    this.config = config;
    this.hotEntries =
        new InMemoryLRU<>(
            (int) Math.max(config.getMaxEntries() * config.getpercentageHotKeys() / 100, 1));
    this.memLoader = memLoader;
    this.mem = mem;
    this.store = memLoader.getStore();

    ChronicleMapCacheMetrics metrics = new ChronicleMapCacheMetrics(metricMaker);
    metrics.registerCallBackMetrics(def.name(), this);
  }

  @SuppressWarnings({"unchecked", "cast", "rawtypes"})
  static <K, V> ChronicleMapStore<K, V> createOrRecoverStore(
      PersistentCacheDef<K, V> def,
      ChronicleMapCacheConfig config,
      MetricMaker metricMaker,
      Injector injector)
      throws IOException {
    CacheSerializers.registerCacheDef(def);

    final Class<KeyWrapper<K>> keyWrapperClass = (Class<KeyWrapper<K>>) (Class) KeyWrapper.class;
    final Class<TimedValue<V>> valueWrapperClass = (Class<TimedValue<V>>) (Class) TimedValue.class;

    final ChronicleMapBuilder<KeyWrapper<K>, TimedValue<V>> mapBuilder =
        ChronicleMap.of(keyWrapperClass, valueWrapperClass).name(def.name());

    // Chronicle-map does not allow to custom-serialize boxed primitives
    // such as Boolean, Integer, for which size is statically determined.
    // This means that even though a custom serializer was provided for a primitive
    // it cannot be used.
    if (!mapBuilder.constantlySizedKeys()) {
      mapBuilder.averageKeySize(config.getAverageKeySize());
      mapBuilder.keyMarshaller(new KeyWrapperMarshaller<>(def.name()));
    }

    mapBuilder.averageValueSize(config.getAverageValueSize());
    mapBuilder.valueMarshaller(new TimedValueMarshaller<>(def.name()));

    mapBuilder.entries(config.getMaxEntries());

    mapBuilder.maxBloatFactor(config.getMaxBloatFactor());

    logger.atWarning().log(
        "chronicle-map cannot honour the diskLimit of %s bytes for the %s "
            + "cache, since the file size is pre-allocated rather than being "
            + "a function of the number of entries in the cache",
        def.diskLimit(), def.name());
    ChronicleMap<KeyWrapper<K>, TimedValue<V>> store =
        mapBuilder.createOrRecoverPersistedTo(config.getPersistedFile());

    logger.atInfo().log(
        "Initialized '%s'|version: %s|avgKeySize: %s bytes|avgValueSize:"
            + " %s bytes|entries: %s|maxBloatFactor: %s|remainingAutoResizes:"
            + " %s|percentageFreeSpace: %s",
        def.name(),
        def.version(),
        mapBuilder.constantlySizedKeys() ? "CONSTANT" : config.getAverageKeySize(),
        config.getAverageValueSize(),
        config.getMaxEntries(),
        config.getMaxBloatFactor(),
        store.remainingAutoResizes(),
        store.percentageFreeSpace());

    return new ChronicleMapStore<>(
        store, config, metricMaker, injector.getInstance(ChronicleMapStoreTotalMetrics.class));
  }

  protected PersistentCacheDef<K, V> getCacheDefinition() {
    return cacheDefinition;
  }

  private static class ChronicleMapCacheMetrics {

    private final MetricMaker metricMaker;

    ChronicleMapCacheMetrics(MetricMaker metricMaker) {
      this.metricMaker = metricMaker;
    }

    <K, V> void registerCallBackMetrics(String name, ChronicleMapCacheImpl<K, V> cache) {
      String sanitizedName = metricMaker.sanitizeMetricName(name);
      String HOT_KEYS_CAPACITY_METRIC = "cache/chroniclemap/hot_keys_capacity_" + sanitizedName;
      String HOT_KEYS_SIZE_METRIC = "cache/chroniclemap/hot_keys_size_" + sanitizedName;

      metricMaker.newConstantMetric(
          HOT_KEYS_CAPACITY_METRIC,
          cache.hotEntries.getCapacity(),
          new Description(
              String.format(
                  "The number of hot cache keys for %s cache that can be kept in memory", name)));

      metricMaker.newCallbackMetric(
          HOT_KEYS_SIZE_METRIC,
          Integer.class,
          new Description(
              String.format(
                  "The number of hot cache keys for %s cache that are currently in memory", name)),
          cache.hotEntries::size);
    }
  }

  public ChronicleMapCacheConfig getConfig() {
    return config;
  }

  @Override
  public V getIfPresent(Object objKey) {
    TimedValue<V> timedValue = mem.getIfPresent(objKey);
    if (timedValue == null) {
      missCount.increment();
      return null;
    }

    return timedValue.getValue();
  }

  @Override
  public V get(K key) throws ExecutionException {
    KeyWrapper<K> keyWrapper = new KeyWrapper<>(key);

    if (mem.isLoadingCache()) {
      TimedValue<V> valueHolder = mem.get(key);
      if (needsRefresh(valueHolder.getCreated())) {
        store.remove(keyWrapper);
        mem.refresh(key);
      }
      return valueHolder.getValue();
    }

    loadExceptionCount.increment();
    throw new UnsupportedOperationException(
        String.format("Could not load value for %s without any loader", key));
  }

  @Override
  public V get(K key, Callable<? extends V> valueLoader) throws ExecutionException {
    try {
      return mem.get(key, () -> getFromStore(key, valueLoader)).getValue();
    } catch (Exception e) {
      if (e instanceof ExecutionException) {
        throw (ExecutionException) e;
      }
      throw new ExecutionException(e);
    }
  }

  private TimedValue<V> getFromStore(K key, Callable<? extends V> valueLoader)
      throws ExecutionException {

    TimedValue<V> valueFromCache = memLoader.loadIfPresent(key);
    if (valueFromCache != null) {
      return valueFromCache;
    }

    V v = null;
    try {
      long start = System.nanoTime();
      v = valueLoader.call();
      totalLoadTime.add(System.nanoTime() - start);
      loadSuccessCount.increment();
    } catch (Exception e) {
      loadExceptionCount.increment();
      throw new ExecutionException(String.format("Could not load key %s", key), e);
    }
    TimedValue<V> timedValue = new TimedValue<>(v);
    putTimedToStore(key, timedValue);
    return timedValue;
  }

  /**
   * Associates the specified value with the specified key. This method should be used when the
   * creation time of the value needs to be preserved, rather than computed at insertion time
   * ({@link #put}. This is typically the case when migrating from an existing cache where the
   * creation timestamp needs to be preserved. See ({@link H2MigrationServlet} for an example.
   *
   * @param key
   * @param value
   * @param created
   */
  @SuppressWarnings("unchecked")
  public void putUnchecked(Object key, Object value, Timestamp created) {
    TimedValue<?> wrappedValue = new TimedValue<>(value, created.toInstant().toEpochMilli());
    KeyWrapper<?> wrappedKey = new KeyWrapper<>(key);
    if (store.tryPut((KeyWrapper<K>) wrappedKey, (TimedValue<V>) wrappedValue)) {
      mem.put((K) key, (TimedValue<V>) wrappedValue);
    }
  }

  /**
   * Associates the specified value with the specified key. This method should be used when the
   * {@link TimedValue} and the {@link KeyWrapper} have already been constructed elsewhere rather
   * than delegate their construction to this cache ({@link #put}. This is typically the case when
   * the key/value are extracted from another chronicle-map cache see ({@link
   * AutoAdjustCachesCommand} for an example.
   *
   * @param wrappedKey The wrapper for the key object
   * @param wrappedValue the wrapper for the value object
   */
  @SuppressWarnings("unchecked")
  public void putUnchecked(KeyWrapper<Object> wrappedKey, TimedValue<Object> wrappedValue) {
    if (store.tryPut((KeyWrapper<K>) wrappedKey, (TimedValue<V>) wrappedValue)) {
      mem.put((K) wrappedKey.getValue(), (TimedValue<V>) wrappedValue);
    }
  }

  @Override
  public void put(K key, V val) {
    TimedValue<V> timedVal = new TimedValue<>(val);
    if (putTimedToStore(key, timedVal)) {
      mem.put(key, timedVal);
    }
  }

  boolean putTimedToStore(K key, TimedValue<V> timedVal) {
    KeyWrapper<K> wrappedKey = new KeyWrapper<>(key);
    boolean putSuccess = store.tryPut(wrappedKey, timedVal);
    if (putSuccess) {
      hotEntries.add(key);
    }
    return putSuccess;
  }

  public void prune() {
    if (!config.getExpireAfterWrite().isZero()) {
      store.forEachEntry(
          c -> {
            if (memLoader.expired(c.value().get().getCreated())) {
              hotEntries.remove(c.key().get().getValue());
              c.context().remove(c);
            }
          });
    }

    if (runningOutOfFreeSpace()) {
      evictColdEntries();
    }
  }

  private boolean needsRefresh(long created) {
    final Duration refreshAfterWrite = config.getRefreshAfterWrite();
    Duration age = Duration.between(Instant.ofEpochMilli(created), TimeUtil.now());
    return !refreshAfterWrite.isZero() && age.compareTo(refreshAfterWrite) > 0;
  }

  protected boolean runningOutOfFreeSpace() {
    return store.remainingAutoResizes() == 0
        && store.percentageFreeSpace() <= config.getPercentageFreeSpaceEvictionThreshold();
  }

  private void evictColdEntries() {
    store.forEachEntryWhile(
        e -> {
          if (!hotEntries.contains(e.key().get().getValue())) {
            e.doRemove();
          }
          return runningOutOfFreeSpace();
        });
  }

  @SuppressWarnings("unchecked")
  @Override
  public void invalidate(Object key) {
    KeyWrapper<K> wrappedKey = (KeyWrapper<K>) new KeyWrapper<>(key);
    store.remove(wrappedKey);
    mem.invalidate(key);
    hotEntries.remove((K) key);
  }

  @Override
  public void invalidateAll() {
    store.clear();
    hotEntries.invalidateAll();
    mem.invalidateAll();
  }

  ChronicleMap<KeyWrapper<K>, TimedValue<V>> getStore() {
    return store;
  }

  @Override
  public long size() {
    return mem.size();
  }

  @Override
  public CacheStats stats() {
    return mem.stats();
  }

  @Override
  public DiskStats diskStats() {
    return new DiskStats(
        store.longSize(),
        config.getPersistedFile().length(),
        hitCount.longValue(),
        missCount.longValue());
  }

  public CacheStats memStats() {
    return mem.stats();
  }

  public void close() {
    store.close();
  }

  public double percentageUsedAutoResizes() {
    return store.percentageUsedAutoResizes();
  }

  public String name() {
    return store.name();
  }
}
