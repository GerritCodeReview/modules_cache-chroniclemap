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
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.LongAdder;
import net.openhft.chronicle.map.ChronicleMap;

class ChronicleMapCacheLoader<K, V> extends CacheLoader<K, TimedValue<V>> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Executor executor;
  private final CacheLoader<K, V> loader;
  private final ChronicleMap<KeyWrapper<K>, TimedValue<V>> store;
  private final LongAdder loadSuccessCount = new LongAdder();
  private final LongAdder loadExceptionCount = new LongAdder();
  private final LongAdder totalLoadTime = new LongAdder();
  private final LongAdder hitCount = new LongAdder();
  private final Duration expireAfterWrite;

  ChronicleMapCacheLoader(
      Executor executor,
      ChronicleMap<KeyWrapper<K>, TimedValue<V>> store,
      CacheLoader<K, V> loader,
      Duration expireAfterWrite) {
    this.executor = executor;
    this.store = store;
    this.loader = loader;
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

      long start = System.nanoTime();
      TimedValue<V> loadedValue = new TimedValue<>(loader.load(key));
      loadSuccessCount.increment();
      totalLoadTime.add(System.nanoTime() - start);

      executor.execute(() -> store.put(new KeyWrapper<>(key), loadedValue));
      return loadedValue;
    } catch (Exception e) {
      loadExceptionCount.increment();
      throw e;
    }
  }

  @Override
  public ListenableFuture<TimedValue<V>> reload(K key, TimedValue<V> oldValue) throws Exception {
    final long start = System.nanoTime();
    ListenableFuture<V> reloadedValue = loader.reload(key, oldValue.getValue());
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

  LongAdder getLoadSuccessCount() {
    return loadSuccessCount;
  }

  LongAdder getLoadExceptionCount() {
    return loadExceptionCount;
  }

  LongAdder getTotalLoadTime() {
    return totalLoadTime;
  }
}
