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
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.cache.PersistentCache;
import com.google.gerrit.server.cache.PersistentCacheDef;
import com.google.gerrit.server.util.time.TimeUtil;
import java.io.IOException;
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
  private final CacheLoader<K, V> loader;
  private final ChronicleMap<K, TimedValue<V>> store;
  private final LongAdder hitCount = new LongAdder();
  private final LongAdder missCount = new LongAdder();
  private final LongAdder loadSuccessCount = new LongAdder();
  private final LongAdder loadExceptionCount = new LongAdder();
  private final LongAdder totalLoadTime = new LongAdder();
  private final LongAdder evictionCount = new LongAdder();
  private final InMemoryLRU<K> hotEntries;

  @SuppressWarnings("unchecked")
  ChronicleMapCacheImpl(
      PersistentCacheDef<K, V> def,
      ChronicleMapCacheConfig config,
      CacheLoader<K, V> loader,
      MetricMaker metricMaker)
      throws IOException {

    this.config = config;
    this.loader = loader;
    this.hotEntries =
        new InMemoryLRU<>(
            (int) Math.max(config.getMaxEntries() * config.getpercentageHotKeys() / 100, 1));

    ChronicleMapStorageMetrics metrics = new ChronicleMapStorageMetrics(metricMaker);

    final Class<K> keyClass = (Class<K>) def.keyType().getRawType();
    final Class<TimedValue<V>> valueWrapperClass = (Class<TimedValue<V>>) (Class) TimedValue.class;

    final ChronicleMapBuilder<K, TimedValue<V>> mapBuilder =
        ChronicleMap.of(keyClass, valueWrapperClass).name(def.name());

    // Chronicle-map does not allow to custom-serialize boxed primitives
    // such as Boolean, Integer, for which size is statically determined.
    // This means that even though a custom serializer was provided for a primitive
    // it cannot be used.
    if (!mapBuilder.constantlySizedKeys()) {
      mapBuilder.averageKeySize(config.getAverageKeySize());
      mapBuilder.keyMarshaller(new ChronicleMapMarshallerAdapter<>(def.keySerializer()));
    }

    mapBuilder.averageValueSize(config.getAverageValueSize());
    mapBuilder.valueMarshaller(new TimedValueMarshaller<>(def.valueSerializer()));

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
        config.getVersion(),
        mapBuilder.constantlySizedKeys() ? "CONSTANT" : config.getAverageKeySize(),
        config.getAverageValueSize(),
        config.getMaxEntries(),
        config.getMaxBloatFactor(),
        store.remainingAutoResizes(),
        store.percentageFreeSpace());

    metrics.registerCallBackMetrics(def.name(), store, hotEntries);
  }

  private static class ChronicleMapStorageMetrics {

    private final MetricMaker metricMaker;

    ChronicleMapStorageMetrics(MetricMaker metricMaker) {
      this.metricMaker = metricMaker;
    }

    <K, V> void registerCallBackMetrics(
        String name, ChronicleMap<K, TimedValue<V>> store, InMemoryLRU<K> hotEntries) {
      String PERCENTAGE_FREE_SPACE_METRIC = "cache/chroniclemap/percentage_free_space_" + name;
      String REMAINING_AUTORESIZES_METRIC = "cache/chroniclemap/remaining_autoresizes_" + name;
      String HOT_KEYS_CAPACITY_METRIC = "cache/chroniclemap/hot_keys_capacity_" + name;
      String HOT_KEYS_SIZE_METRIC = "cache/chroniclemap/hot_keys_size_" + name;

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
    if (store.containsKey(objKey)) {
      TimedValue<V> vTimedValue = store.get(objKey);
      if (!expired(vTimedValue.getCreated())) {
        hitCount.increment();
        hotEntries.add((K) objKey);
        return vTimedValue.getValue();
      } else {
        invalidate(objKey);
      }
    }
    missCount.increment();
    return null;
  }

  @Override
  public V get(K key) throws ExecutionException {
    if (store.containsKey(key)) {
      TimedValue<V> vTimedValue = store.get(key);
      if (!needsRefresh(vTimedValue.getCreated())) {
        hitCount.increment();
        hotEntries.add(key);
        return vTimedValue.getValue();
      }
    }
    missCount.increment();
    if (loader != null) {
      V v = null;
      try {
        long start = System.nanoTime();
        v = loader.load(key);
        totalLoadTime.add(System.nanoTime() - start);
        loadSuccessCount.increment();
      } catch (Exception e) {
        loadExceptionCount.increment();
        throw new ExecutionException(String.format("Could not load value %s", key), e);
      }
      put(key, v);
      return v;
    }

    loadExceptionCount.increment();
    throw new UnsupportedOperationException(
        String.format("Could not load value for %s without any loader", key));
  }

  @Override
  public V get(K key, Callable<? extends V> valueLoader) throws ExecutionException {
    if (store.containsKey(key)) {
      TimedValue<V> vTimedValue = store.get(key);
      if (!needsRefresh(vTimedValue.getCreated())) {
        hitCount.increment();
        return vTimedValue.getValue();
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
      throw new ExecutionException(String.format("Could not load key %s", key), e);
    }
    put(key, v);
    return v;
  }

  @Override
  public void put(K key, V val) {
    TimedValue<V> wrapped = new TimedValue<>(val);
    store.put(key, wrapped);
    hotEntries.add(key);
  }

  public void prune() {
    if (!config.getExpireAfterWrite().isZero()) {
      store.forEachEntry(
          c -> {
            if (expired(c.value().get().getCreated())) {
              hotEntries.remove(c.key().get());
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
          if (!hotEntries.contains(e.key().get())) {
            e.doRemove();
          }
          return runningOutOfFreeSpace();
        });
  }

  @Override
  public void invalidate(Object key) {
    store.remove(key);
    hotEntries.remove((K) key);
  }

  @Override
  public void invalidateAll() {
    store.clear();
    hotEntries.invalidateAll();
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
