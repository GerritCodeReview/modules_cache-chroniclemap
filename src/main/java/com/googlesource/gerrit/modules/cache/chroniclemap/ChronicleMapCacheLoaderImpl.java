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
import java.util.concurrent.Executor;
import net.openhft.chronicle.map.ChronicleMap;

class ChronicleMapCacheLoaderImpl<K, V> extends CacheLoader<K, TimedValue<V>> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Executor executor;
  private final CacheLoader<K, V> loader;
  private final ChronicleMap<KeyWrapper<K>, TimedValue<V>> store;

  ChronicleMapCacheLoaderImpl(
      Executor executor,
      ChronicleMap<KeyWrapper<K>, TimedValue<V>> store,
      CacheLoader<K, V> loader) {
    this.executor = executor;
    this.store = store;
    this.loader = loader;
  }

  @Override
  public TimedValue<V> load(K key) throws Exception {
    try (TraceTimer timer =
        TraceContext.newTimer(
            "Loading value from cache", Metadata.builder().cacheKey(key.toString()).build())) {
      TimedValue<V> h = store.get(new KeyWrapper<>(key));
      if (h != null) {
        return h;
      }

      TimedValue<V> loadedValue = new TimedValue<>(loader.load(key));
      executor.execute(() -> store.put(new KeyWrapper<>(key), loadedValue));
      return loadedValue;
    }
  }

  @Override
  public ListenableFuture<TimedValue<V>> reload(K key, TimedValue<V> oldValue) throws Exception {
    ListenableFuture<V> reloadedValue = loader.reload(key, oldValue.getValue());
    Futures.addCallback(
        reloadedValue,
        new FutureCallback<V>() {
          @Override
          public void onSuccess(V result) {
            store.put(new KeyWrapper<>(key), new TimedValue<>(result));
          }

          @Override
          public void onFailure(Throwable t) {
            logger.atWarning().withCause(t).log("Unable to reload cache value");
          }
        },
        executor);

    return Futures.transform(reloadedValue, TimedValue::new, executor);
  }
}
