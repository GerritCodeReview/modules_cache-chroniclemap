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
import com.google.common.cache.Cache;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.cache.PersistentCache;
import com.google.gerrit.server.cache.PersistentCacheDef;
import com.google.gerrit.server.util.time.TimeUtil;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.LongAdder;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;

public class ChronicleMapCacheImpl<K, V> extends AbstractLoadingCache<K, V>
    implements PersistentCache {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ChronicleMapCacheConfig config;
  private final ChronicleMap<KeyWrapper<K>, TimedValue<V>> store;
  private final LongAdder hitCount = new LongAdder();
  private final LongAdder missCount = new LongAdder();
  private final LongAdder loadSuccessCount = new LongAdder();
  private final LongAdder loadExceptionCount = new LongAdder();
  private final LongAdder totalLoadTime = new LongAdder();
  private final LongAdder evictionCount = new LongAdder();
  private final InMemoryLRU<K> hotEntries;
  private final PersistentCacheDef<K, V> cacheDefinition;
  private Optional<Cache<K, TimedValue<V>>> mem;

  @SuppressWarnings({"unchecked", "cast", "rawtypes"})
  ChronicleMapCacheImpl(
      PersistentCacheDef<K, V> def,
      ChronicleMapCacheConfig config,
      MetricMaker metricMaker,
      @Nullable Cache<K, TimedValue<V>> mem)
      throws IOException {
    CacheSerializers.registerCacheDef(def);

    this.cacheDefinition = def;
    this.config = config;
    this.hotEntries =
        new InMemoryLRU<>(
            (int) Math.max(config.getMaxEntries() * config.getpercentageHotKeys() / 100, 1));
    this.mem = Optional.ofNullable(mem);

    ChronicleMapStorageMetrics metrics = new ChronicleMapStorageMetrics(metricMaker);

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
    store = mapBuilder.createOrRecoverPersistedTo(config.getPersistedFile());

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

    metrics.registerCallBackMetrics(def.name(), store, hotEntries);
  }

  void setMem(Cache<K, TimedValue<V>> mem) {
    this.mem = Optional.ofNullable(mem);
  }

  protected PersistentCacheDef<K, V> getCacheDefinition() {
    return cacheDefinition;
  }

  private static class ChronicleMapStorageMetrics {

    private final MetricMaker metricMaker;

    ChronicleMapStorageMetrics(MetricMaker metricMaker) {
      this.metricMaker = metricMaker;
    }

    <K, V> void registerCallBackMetrics(
        String name, ChronicleMap<KeyWrapper<K>, TimedValue<V>> store, InMemoryLRU<K> hotEntries) {
      String sanitizedName = metricMaker.sanitizeMetricName(name);
      String PERCENTAGE_FREE_SPACE_METRIC =
          "cache/chroniclemap/percentage_free_space_" + sanitizedName;
      String REMAINING_AUTORESIZES_METRIC =
          "cache/chroniclemap/remaining_autoresizes_" + sanitizedName;
      String HOT_KEYS_CAPACITY_METRIC = "cache/chroniclemap/hot_keys_capacity_" + sanitizedName;
      String HOT_KEYS_SIZE_METRIC = "cache/chroniclemap/hot_keys_size_" + sanitizedName;

      metricMaker.newCallbackMetric(
          PERCENTAGE_FREE_SPACE_METRIC,
          Long.class,
          new Description(
              String.format("The amount of free space in the %s cache as a percentage", name)),
          () -> (long) store.percentageFreeSpace());

      metricMaker.newCallbackMetric(
          REMAINING_AUTORESIZES_METRIC,
          Integer.class,
          new Description(
              String.format(
                  "The number of times the %s cache can automatically expand its capacity", name)),
          store::remainingAutoResizes);

      metricMaker.newConstantMetric(
          HOT_KEYS_CAPACITY_METRIC,
          hotEntries.getCapacity(),
          new Description(
              String.format(
                  "The number of hot cache keys for %s cache that can be kept in memory", name)));

      metricMaker.newCallbackMetric(
          HOT_KEYS_SIZE_METRIC,
          Integer.class,
          new Description(
              String.format(
                  "The number of hot cache keys for %s cache that are currently in memory", name)),
          hotEntries::size);
    }
  }

  public ChronicleMapCacheConfig getConfig() {
    return config;
  }

  @Override
  public V getIfPresent(Object objKey) {
    return getOptional(objKey).map(TimedValue::getValue).orElse(null);
  }

  @SuppressWarnings("unchecked")
  private Optional<TimedValue<V>> getOptional(Object objKey) {
    Optional<TimedValue<V>> h = mem.flatMap(m -> Optional.ofNullable(m.getIfPresent(objKey)));
    return h.or(
        () -> {
          KeyWrapper<K> keyWrapper = (KeyWrapper<K>) new KeyWrapper<>(objKey);
          if (store.containsKey(keyWrapper)) {
            TimedValue<V> vTimedValue = store.get(keyWrapper);
            if (!expired(vTimedValue.getCreated())) {
              hitCount.increment();
              hotEntries.add((K) objKey);
              mem.ifPresent(m -> m.put((K) objKey, vTimedValue));
              return Optional.of(vTimedValue);
            }
            invalidate(objKey);
          }
          missCount.increment();
          return Optional.empty();
        });
  }

  @Override
  public V get(K key) throws ExecutionException {
    if (mem.map(m -> m instanceof LoadingCache).orElse(false)) {
      LoadingCache<K, TimedValue<V>> asLoadingCache = (LoadingCache<K, TimedValue<V>>) mem.get();
      TimedValue<V> valueHolder = asLoadingCache.get(key);
      if (needsRefresh(valueHolder.getCreated())) {
        asLoadingCache.refresh(key);
      }
      return valueHolder.getValue();
    }

    KeyWrapper<K> keyWrapper = new KeyWrapper<>(key);
    if (store.containsKey(keyWrapper)) {
      TimedValue<V> vTimedValue = store.get(keyWrapper);
      if (!needsRefresh(vTimedValue.getCreated())) {
        hitCount.increment();
        hotEntries.add(key);
        mem.ifPresent(m -> m.put(key, vTimedValue));
        return vTimedValue.getValue();
      }
    }

    missCount.increment();

    loadExceptionCount.increment();
    throw new ExecutionException(
        new IllegalArgumentException(String.format("No values associated with key %s", key)));
  }

  @Override
  public V get(K key, Callable<? extends V> valueLoader) throws ExecutionException {
    if (mem.isPresent()) {
      return mem.get().get(key, () -> getFromStore(key, valueLoader)).getValue();
    }

    return getFromStore(key, valueLoader).getValue();
  }

  private TimedValue<V> getFromStore(K key, Callable<? extends V> valueLoader) {
    KeyWrapper<K> keyWrapper = new KeyWrapper<>(key);
    if (store.containsKey(keyWrapper)) {
      TimedValue<V> vTimedValue = store.get(keyWrapper);
      if (!needsRefresh(vTimedValue.getCreated())) {
        hitCount.increment();
        return vTimedValue;
      }
    }
    missCount.increment();
    V v = null;
    try {
      long start = System.nanoTime();
      v = valueLoader.call();
      totalLoadTime.add(System.nanoTime() - start);
      loadSuccessCount.increment();
    } catch (Exception e) {
      loadExceptionCount.increment();
      throw new UncheckedExecutionException(String.format("Could not load key %s", key), e);
    }
    put(key, v);
    return new TimedValue<>(v);
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
    store.put((KeyWrapper<K>) wrappedKey, (TimedValue<V>) wrappedValue);
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
    store.put((KeyWrapper<K>) wrappedKey, (TimedValue<V>) wrappedValue);
  }

  @Override
  public void put(K key, V val) {
    putTimed(key, new TimedValue<>(val));
  }

  void putTimed(K key, TimedValue<V> timedVal) {
    KeyWrapper<K> wrappedKey = new KeyWrapper<>(key);
    store.put(wrappedKey, timedVal);
    hotEntries.add(key);
  }

  public void prune() {
    if (!config.getExpireAfterWrite().isZero()) {
      store.forEachEntry(
          c -> {
            if (expired(c.value().get().getCreated())) {
              hotEntries.remove(c.key().get().getValue());
              c.context().remove(c);
            }
          });
    }

    if (runningOutOfFreeSpace()) {
      evictColdEntries();
    }
  }

  private boolean expired(long created) {
    Duration expireAfterWrite = config.getExpireAfterWrite();
    Duration age = Duration.between(Instant.ofEpochMilli(created), TimeUtil.now());
    return !expireAfterWrite.isZero() && age.compareTo(expireAfterWrite) > 0;
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
    hotEntries.remove(wrappedKey.getValue());
  }

  @Override
  public void invalidateAll() {
    store.clear();
    hotEntries.invalidateAll();
  }

  ChronicleMap<KeyWrapper<K>, TimedValue<V>> getStore() {
    return store;
  }

  @Override
  public long size() {
    return store.size();
  }

  @Override
  public CacheStats stats() {
    return new CacheStats(
        hitCount.longValue(),
        missCount.longValue(),
        loadSuccessCount.longValue(),
        loadExceptionCount.longValue(),
        totalLoadTime.longValue(),
        evictionCount.longValue());
  }

  @Override
  public DiskStats diskStats() {
    return new DiskStats(
        size(), config.getPersistedFile().length(), hitCount.longValue(), missCount.longValue());
  }

  public void close() {
    store.close();
  }
}
