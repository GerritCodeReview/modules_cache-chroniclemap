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

    return Futures.transform(reloadedValue, v -> new TimedValue<>(v), executor);
  }
}
