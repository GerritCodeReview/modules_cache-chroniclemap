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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.cache.CacheBackend;
import com.google.gerrit.server.cache.MemoryCacheFactory;
import com.google.gerrit.server.cache.PersistentCacheDef;
import com.google.gerrit.server.cache.PersistentCacheFactory;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.logging.LoggingContextAwareExecutorService;
import com.google.gerrit.server.logging.LoggingContextAwareScheduledExecutorService;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

@Singleton
class ChronicleMapCacheFactory implements PersistentCacheFactory, LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final MemoryCacheFactory memCacheFactory;
  private final Config config;
  private final ChronicleMapCacheConfig.Factory configFactory;
  private final MetricMaker metricMaker;
  private final DynamicMap<Cache<?, ?>> cacheMap;
  private final List<ChronicleMapCacheImpl<?, ?>> caches;
  private final ScheduledExecutorService cleanup;
  private final Path cacheDir;

  private final LoggingContextAwareExecutorService executor;

  @Inject
  ChronicleMapCacheFactory(
      MemoryCacheFactory memCacheFactory,
      @GerritServerConfig Config cfg,
      SitePaths site,
      ChronicleMapCacheConfig.Factory configFactory,
      DynamicMap<Cache<?, ?>> cacheMap,
      MetricMaker metricMaker) {
    this.memCacheFactory = memCacheFactory;
    this.config = cfg;
    this.configFactory = configFactory;
    this.metricMaker = metricMaker;
    this.caches = new LinkedList<>();
    this.cacheDir = getCacheDir(site, cfg.getString("cache", null, "directory"));
    this.cacheMap = cacheMap;
    this.cleanup =
        new LoggingContextAwareScheduledExecutorService(
            Executors.newScheduledThreadPool(
                1,
                new ThreadFactoryBuilder()
                    .setNameFormat("ChronicleMap-Prune-%d")
                    .setDaemon(true)
                    .build()));
    this.executor =
        new LoggingContextAwareExecutorService(
            Executors.newFixedThreadPool(
                1, new ThreadFactoryBuilder().setNameFormat("ChronicleMap-Store-%d").build()));
  }

  @Override
  public <K, V> Cache<K, V> build(PersistentCacheDef<K, V> in, CacheBackend backend) {
    if (isInMemoryCache(in)) {
      return memCacheFactory.build(in, backend);
    }
    ChronicleMapCacheConfig config =
        configFactory.create(
            in.configKey(),
            fileName(cacheDir, in.name(), in.version()),
            in.expireAfterWrite(),
            in.refreshAfterWrite());
    return build(in, backend, config);
  }

  @SuppressWarnings("unchecked")
  @VisibleForTesting
  <K, V> Cache<K, V> build(
      PersistentCacheDef<K, V> in, CacheBackend backend, ChronicleMapCacheConfig config) {
    ChronicleMapCacheDefProxy<K, V> def = new ChronicleMapCacheDefProxy<>(in);

    ChronicleMapCacheImpl<K, V> cache;
    try {
      cache =
          new ChronicleMapCacheImpl<>(
              in,
              config,
              metricMaker,
              (Cache<K, TimedValue<V>>) memCacheFactory.build(def, backend));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    synchronized (caches) {
      caches.add(cache);
    }
    return cache;
  }

  @Override
  public <K, V> LoadingCache<K, V> build(
      PersistentCacheDef<K, V> in, CacheLoader<K, V> loader, CacheBackend backend) {
    if (isInMemoryCache(in)) {
      return memCacheFactory.build(in, loader, backend);
    }
    ChronicleMapCacheConfig config =
        configFactory.create(
            in.configKey(),
            fileName(cacheDir, in.name(), in.version()),
            in.expireAfterWrite(),
            in.refreshAfterWrite());
    return build(in, loader, backend, config);
  }

  @SuppressWarnings("unchecked")
  @VisibleForTesting
  public <K, V> LoadingCache<K, V> build(
      PersistentCacheDef<K, V> in,
      CacheLoader<K, V> loader,
      CacheBackend backend,
      ChronicleMapCacheConfig config) {
    ChronicleMapCacheImpl<K, V> cache;
    ChronicleMapCacheDefProxy<K, V> def = new ChronicleMapCacheDefProxy<>(in);

    try {
      cache = new ChronicleMapCacheImpl<>(in, config, metricMaker, null);

      Cache<K, TimedValue<V>> mem =
          (Cache<K, TimedValue<V>>)
              memCacheFactory.build(
                  def,
                  (CacheLoader<K, V>)
                      new ChronicleMapCacheLoaderImpl<>(executor, cache.getStore(), loader),
                  backend);

      cache.setMem(mem);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    synchronized (caches) {
      caches.add(cache);
    }
    return cache;
  }

  @Override
  public void onStop(String plugin) {
    synchronized (caches) {
      for (Map.Entry<String, Provider<Cache<?, ?>>> entry : cacheMap.byPlugin(plugin).entrySet()) {
        Cache<?, ?> cache = entry.getValue().get();
        if (caches.remove(cache)) {
          ((ChronicleMapCacheImpl<?, ?>) cache).close();
        }
      }
    }
  }

  private <K, V> boolean isInMemoryCache(PersistentCacheDef<K, V> in) {
    return cacheDir == null
        || config.getLong("cache", in.configKey(), "diskLimit", in.diskLimit()) <= 0;
  }

  @Override
  public void start() {
    for (ChronicleMapCacheImpl<?, ?> cache : caches) {
      cleanup.scheduleWithFixedDelay(cache::prune, 30, 30, TimeUnit.SECONDS);
    }
  }

  @Override
  public void stop() {
    cleanup.shutdownNow();
  }

  public static File fileName(Path cacheDir, String name, Integer version) {
    return cacheDir.resolve(String.format("%s_%s.dat", name, version)).toFile();
  }

  protected static Path getCacheDir(SitePaths site, String name) {
    if (name == null) {
      return null;
    }
    Path loc = site.resolve(name);
    if (!Files.exists(loc)) {
      try {
        Files.createDirectories(loc);
      } catch (IOException e) {
        logger.atWarning().log("Can't create disk cache: %s", loc.toAbsolutePath());
        return null;
      }
    }
    if (!Files.isWritable(loc)) {
      logger.atWarning().log("Can't write to disk cache: %s", loc.toAbsolutePath());
      return null;
    }
    logger.atInfo().log("Enabling disk cache %s", loc.toAbsolutePath());
    return loc;
  }
}
