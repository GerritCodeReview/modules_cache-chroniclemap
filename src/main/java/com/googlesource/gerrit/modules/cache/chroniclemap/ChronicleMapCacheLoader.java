// Copyright (C) 2021 The Android Open Source Project
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

import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.util.time.TimeUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.LongAdder;
import net.openhft.chronicle.map.ChronicleMap;

class ChronicleMapCacheLoader<K, V> extends CacheLoader<K, TimedValue<V>> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Executor executor;
  private final Optional<CacheLoader<K, V>> loader;
  private final ChronicleMap<KeyWrapper<K>, TimedValue<V>> store;
  private final LongAdder loadSuccessCount = new LongAdder();
  private final LongAdder loadExceptionCount = new LongAdder();
  private final LongAdder totalLoadTime = new LongAdder();
  private final LongAdder hitCount = new LongAdder();
  private final LongAdder missCount = new LongAdder();
  private final Duration expireAfterWrite;

  ChronicleMapCacheLoader(
      Executor executor,
      ChronicleMap<KeyWrapper<K>, TimedValue<V>> store,
      CacheLoader<K, V> loader,
      Duration expireAfterWrite) {
    this.executor = executor;
    this.store = store;
    this.loader = Optional.of(loader);
    this.expireAfterWrite = expireAfterWrite;
  }

  ChronicleMapCacheLoader(
      Executor executor,
      ChronicleMap<KeyWrapper<K>, TimedValue<V>> store,
      Duration expireAfterWrite) {
    this.executor = executor;
    this.store = store;
    this.loader = Optional.empty();
    this.expireAfterWrite = expireAfterWrite;
  }

  @Override
  public TimedValue<V> load(K key) throws Exception {
    try (TraceTimer timer =
        TraceContext.newTimer(
            "Loading value from cache", Metadata.builder().cacheKey(key.toString()).build())) {
      TimedValue<V> h = store.get(new KeyWrapper<>(key));
      if (h != null && !expired(h.getCreated())) {
        hitCount.increment();
        return h;
      }

      if (loader.isPresent()) {
        missCount.increment();
        long start = System.nanoTime();
        TimedValue<V> loadedValue = new TimedValue<>(loader.get().load(key));
        loadSuccessCount.increment();
        totalLoadTime.add(System.nanoTime() - start);
        executor.execute(() -> store.put(new KeyWrapper<>(key), loadedValue));
        return loadedValue;
      }

      throw new UnsupportedOperationException("No loader defined");
    } catch (Exception e) {
      logger.atWarning().withCause(e).log("Unable to load a value for key='%s'", key);
      loadExceptionCount.increment();
      throw e;
    }
  }

  @Override
  public ListenableFuture<TimedValue<V>> reload(K key, TimedValue<V> oldValue) throws Exception {
    if (!loader.isPresent()) {
      throw new IllegalStateException("No loader defined");
    }

    final long start = System.nanoTime();
    ListenableFuture<V> reloadedValue = loader.get().reload(key, oldValue.getValue());
    Futures.addCallback(
        reloadedValue,
        new FutureCallback<V>() {
          @Override
          public void onSuccess(V result) {
            store.put(new KeyWrapper<>(key), new TimedValue<>(result));
            loadSuccessCount.increment();
            totalLoadTime.add(System.nanoTime() - start);
          }

          @Override
          public void onFailure(Throwable t) {
            logger.atWarning().withCause(t).log("Unable to reload cache value");
            loadExceptionCount.increment();
          }
        },
        executor);

    return Futures.transform(reloadedValue, TimedValue::new, executor);
  }

  private boolean expired(long created) {
    Duration age = Duration.between(Instant.ofEpochMilli(created), TimeUtil.now());
    return !expireAfterWrite.isZero() && age.compareTo(expireAfterWrite) > 0;
  }

  long getLoadSuccessCount() {
    return loadSuccessCount.longValue();
  }

  long getLoadExceptionCount() {
    return loadExceptionCount.longValue();
  }

  long getTotalLoadTime() {
    return totalLoadTime.longValue();
  }

  long getMissCount() {
    return missCount.longValue();
  }

  InMemoryCache<K, V> asInMemoryCacheBypass() {
    return new InMemoryCache<K, V>() {

      @SuppressWarnings("unchecked")
      @Override
      public TimedValue<V> getIfPresent(Object key) {
        try {
          return load((K) key);
        } catch (Exception e) {
          return null;
        }
      }

      @Override
      public TimedValue<V> get(K key, Callable<? extends TimedValue<V>> valueLoader)
          throws Exception {
        return valueLoader.call();
      }

      @Override
      public void put(K key, TimedValue<V> value) {}

      @Override
      public boolean isLoadingCache() {
        return true;
      }

      @Override
      public TimedValue<V> get(K key) throws ExecutionException {
        try {
          return load(key);
        } catch (Exception e) {
          throw new ExecutionException(e);
        }
      }

      @Override
      public void refresh(K key) {}

      @Override
      public CacheStats stats() {
        return new CacheStats(0, 0, 0, 0, 0, 0);
      }

      @Override
      public long size() {
        return 0;
      }
    };
  }
}
