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

import com.google.gerrit.server.cache.PersistentCacheDef;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class CacheSerializers {
  // TODO: Add other caches, such as
  //  comment_context_5.dat
  //  gerrit_file_diff_4.dat
  //  git_file_diff_0.dat
  //  git_modified_files_1.dat
  //  modified_files_1.dat

  private static final Map<String, CacheSerializer<?>> keySerializers = new ConcurrentHashMap<>();

  private static final Map<String, CacheSerializer<?>> valueSerializers = new ConcurrentHashMap<>();

  static <K, V> void registerCacheDef(PersistentCacheDef<K, V> def) {
    String cacheName = def.name();
    keySerializers.computeIfAbsent(cacheName, (name) -> def.keySerializer());
    valueSerializers.computeIfAbsent(cacheName, (name) -> def.valueSerializer());
  }

  @SuppressWarnings("unchecked")
  public static <K> CacheSerializer<K> getKeySerializer(String name) {
    if (keySerializers.containsKey(name)) {
      return (CacheSerializer<K>) keySerializers.get(name);
    }
    throw new IllegalStateException("Could not find key serializer for " + name);
  }

  @SuppressWarnings("unchecked")
  public static <V> CacheSerializer<V> getValueSerializer(String name) {
    if (valueSerializers.containsKey(name)) {
      return (CacheSerializer<V>) valueSerializers.get(name);
    }
    throw new IllegalStateException("Could not find value serializer for " + name);
  }
}
