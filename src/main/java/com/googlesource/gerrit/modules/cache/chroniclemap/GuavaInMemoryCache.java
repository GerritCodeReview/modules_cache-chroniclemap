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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.common.Nullable;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

class GuavaInMemoryCache<K, V> implements InMemoryCache<K, V> {
  private Cache<K, TimedValue<V>> guavaCache;
  private final boolean loadingCache;

  public GuavaInMemoryCache(Cache<K, TimedValue<V>> guavaCache, boolean loadingCache) {
    this.guavaCache = guavaCache;
    this.loadingCache = loadingCache;
  }

  @Override
  public @Nullable TimedValue<V> getIfPresent(Object key) {
    return guavaCache.getIfPresent(key);
  }

  @Override
  public TimedValue<V> get(K key, Callable<? extends TimedValue<V>> valueLoader) throws Exception {
    return guavaCache.get(key, valueLoader);
  }

  @Override
  public void put(K key, TimedValue<V> value) {
    guavaCache.put(key, value);
  }

  @Override
  public boolean isLoadingCache() {
    return loadingCache;
  }

  @Override
  public TimedValue<V> get(K key) throws ExecutionException {
    if (loadingCache) {
      return ((LoadingCache<K, TimedValue<V>>) guavaCache).get(key);
    }

    TimedValue<V> cachedValue = getIfPresent(key);
    if (cachedValue != null) {
      return cachedValue;
    }

    throw new UnsupportedOperationException(
        String.format("Could not load value for %s without any loader", key));
  }

  @Override
  public void refresh(K key) {
    if (isLoadingCache()) {
      ((LoadingCache<K, TimedValue<V>>) guavaCache).refresh(key);
    }
  }

  @Override
  public CacheStats stats() {
    return guavaCache.stats();
  }

  @Override
  public long size() {
    return guavaCache.size();
  }

  @Override
  public void invalidate(Object key) {
    guavaCache.invalidate(key);
  }

  @Override
  public void invalidateAll() {
    guavaCache.invalidateAll();
  }
}
