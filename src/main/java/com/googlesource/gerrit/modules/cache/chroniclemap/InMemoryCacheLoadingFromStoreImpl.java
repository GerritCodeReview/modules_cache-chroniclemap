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

import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.common.Nullable;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

class InMemoryCacheLoadingFromStoreImpl<K, V> implements InMemoryCache<K, V> {
  private final LoadingCache<K, TimedValue<V>> loadingFromStoreCache;
  private final boolean loadingFromSource;

  /**
   * Creates an in-memory cache backed by a LoadingCache linked to loader from ChronicleMap.
   *
   * @param loadingFromStoreCache LoadingCache linked to loader from ChronicleMap
   * @param loadingFromSource true if the loadingFromStoreCache is also loading from the data source
   */
  InMemoryCacheLoadingFromStoreImpl(
      LoadingCache<K, TimedValue<V>> loadingFromStoreCache, boolean loadingFromSource) {
    this.loadingFromStoreCache = loadingFromStoreCache;
    this.loadingFromSource = loadingFromSource;
  }

  @Override
  public @Nullable TimedValue<V> getIfPresent(Object key) {
    return loadingFromStoreCache.getIfPresent(key);
  }

  @Override
  public TimedValue<V> get(K key, Callable<? extends TimedValue<V>> valueLoader) throws Exception {
    return loadingFromStoreCache.get(key, valueLoader);
  }

  @Override
  public void put(K key, TimedValue<V> value) {
    loadingFromStoreCache.put(key, value);
  }

  @Override
  public boolean isLoadingCache() {
    return loadingFromSource;
  }

  @Override
  public TimedValue<V> get(K key) throws ExecutionException {
    TimedValue<V> cachedValue = getIfPresent(key);
    if (cachedValue != null) {
      return cachedValue;
    }

    if (loadingFromSource) {
      return loadingFromStoreCache.get(key);
    }

    throw new UnsupportedOperationException(
        String.format("Could not load value for %s without any loader", key));
  }

  @Override
  public void refresh(K key) {
    if (loadingFromSource) {
      loadingFromStoreCache.refresh(key);
    }
  }

  @Override
  public CacheStats stats() {
    return loadingFromStoreCache.stats();
  }

  @Override
  public long size() {
    return loadingFromStoreCache.size();
  }

  @Override
  public void invalidate(Object key) {
    loadingFromStoreCache.invalidate(key);
  }

  @Override
  public void invalidateAll() {
    loadingFromStoreCache.invalidateAll();
  }
}
