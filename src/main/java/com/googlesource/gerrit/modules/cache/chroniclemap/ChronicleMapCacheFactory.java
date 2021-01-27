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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.cache.CacheBackend;
import com.google.gerrit.server.cache.MemoryCacheFactory;
import com.google.gerrit.server.cache.PersistentCacheDef;
import com.google.gerrit.server.cache.PersistentCacheFactory;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.logging.LoggingContextAwareScheduledExecutorService;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

@Singleton
class ChronicleMapCacheFactory implements PersistentCacheFactory, LifecycleListener {
  private final MemoryCacheFactory memCacheFactory;
  private final Config config;
  private final ChronicleMapCacheConfig.Factory configFactory;
  private final DynamicMap<Cache<?, ?>> cacheMap;
  private final List<ChronicleMapCacheImpl<?, ?>> caches;
  private final ScheduledExecutorService cleanup;

  @Inject
  ChronicleMapCacheFactory(
      MemoryCacheFactory memCacheFactory,
      @GerritServerConfig Config cfg,
      ChronicleMapCacheConfig.Factory configFactory,
      DynamicMap<Cache<?, ?>> cacheMap) {
    this.memCacheFactory = memCacheFactory;
    this.config = cfg;
    this.configFactory = configFactory;
    this.caches = new LinkedList<>();
    this.cacheMap = cacheMap;
    this.cleanup =
        new LoggingContextAwareScheduledExecutorService(
            Executors.newScheduledThreadPool(
                1,
                new ThreadFactoryBuilder()
                    .setNameFormat("ChronicleMap-Prune-%d")
                    .setDaemon(true)
                    .build()));
  }

  @Override
  public <K, V> Cache<K, V> build(PersistentCacheDef<K, V> in, CacheBackend backend) {
    if (inMemoryCache(in)) {
      return memCacheFactory.build(in, backend);
    }
    ChronicleMapCacheConfig config =
        configFactory.create(
            in.name(),
            in.configKey(),
            in.diskLimit(),
            in.expireAfterWrite(),
            in.refreshAfterWrite());
    ChronicleMapCacheImpl<K, V> cache;
    try {
      cache = new ChronicleMapCacheImpl<>(in, config, null);
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
    if (inMemoryCache(in)) {
      return memCacheFactory.build(in, loader, backend);
    }
    ChronicleMapCacheConfig config =
        configFactory.create(
            in.name(),
            in.configKey(),
            in.diskLimit(),
            in.expireAfterWrite(),
            in.refreshAfterWrite());
    ChronicleMapCacheImpl<K, V> cache;
    try {
      cache = new ChronicleMapCacheImpl<>(in, config, loader);
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

  private <K, V> boolean inMemoryCache(PersistentCacheDef<K, V> in) {
    return config.getLong("cache", in.configKey(), "diskLimit", in.diskLimit()) <= 0;
  }

  @Override
  public void start() {
    for (ChronicleMapCacheImpl<?, ?> cache : caches) {
      if (!cache.getConfig().getExpireAfterWrite().isZero()) {
        cleanup.scheduleWithFixedDelay(cache::prune, 30, 30, TimeUnit.SECONDS);
      }
    }
  }

  @Override
  public void stop() {
    cleanup.shutdownNow();
  }
}
