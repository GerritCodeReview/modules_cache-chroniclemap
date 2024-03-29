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
import com.google.errorprone.annotations.CompatibleWith;
import com.google.gerrit.common.Nullable;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

interface InMemoryCache<K, V> {

  @Nullable
  TimedValue<V> getIfPresent(@CompatibleWith("K") Object key);

  TimedValue<V> get(K key) throws ExecutionException;

  TimedValue<V> get(K key, Callable<? extends TimedValue<V>> valueLoader) throws Exception;

  void put(K key, TimedValue<V> value);

  void invalidate(@CompatibleWith("K") Object key);

  boolean isLoadingCache();

  void refresh(K key);

  CacheStats stats();

  long size();

  void invalidateAll();
}
