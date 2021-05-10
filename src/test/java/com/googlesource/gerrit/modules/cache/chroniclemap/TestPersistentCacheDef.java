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

import com.google.common.cache.CacheLoader;
import com.google.common.cache.Weigher;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.cache.PersistentCacheDef;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.StringCacheSerializer;
import com.google.inject.TypeLiteral;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public class TestPersistentCacheDef implements PersistentCacheDef<String, String> {

  private static final Integer DEFAULT_DISK_LIMIT = 1024;

  private final String loadedValue;
  private final Duration expireAfterWrite;
  private final Duration refreshAfterWrite;
  private final Integer diskLimit;
  private final CacheSerializer<String> keySerializer;
  private final CacheSerializer<String> valueSerializer;

  public TestPersistentCacheDef(
      String loadedValue,
      @Nullable Duration expireAfterWrite,
      @Nullable Duration refreshAfterWrite) {

    this.loadedValue = loadedValue;
    this.expireAfterWrite = expireAfterWrite;
    this.refreshAfterWrite = refreshAfterWrite;
    this.diskLimit = DEFAULT_DISK_LIMIT;
    this.keySerializer = StringCacheSerializer.INSTANCE;
    this.valueSerializer = StringCacheSerializer.INSTANCE;
  }

  public TestPersistentCacheDef(String loadedValue, Integer diskLimit) {

    this.loadedValue = loadedValue;
    this.expireAfterWrite = null;
    this.refreshAfterWrite = null;
    this.diskLimit = diskLimit;
    this.keySerializer = StringCacheSerializer.INSTANCE;
    this.valueSerializer = StringCacheSerializer.INSTANCE;
  }

  public TestPersistentCacheDef(
      String loadedValue,
      @Nullable CacheSerializer<String> keySerializer,
      @Nullable CacheSerializer<String> valueSerializer) {

    this.loadedValue = loadedValue;
    this.expireAfterWrite = Duration.ZERO;
    this.refreshAfterWrite = Duration.ZERO;
    this.diskLimit = DEFAULT_DISK_LIMIT;
    this.keySerializer = Optional.ofNullable(keySerializer).orElse(StringCacheSerializer.INSTANCE);
    this.valueSerializer =
        Optional.ofNullable(valueSerializer).orElse(StringCacheSerializer.INSTANCE);
  }

  @Override
  public long diskLimit() {
    return diskLimit;
  }

  @Override
  public int version() {
    return 0;
  }

  @Override
  public CacheSerializer<String> keySerializer() {
    return keySerializer;
  }

  @Override
  public CacheSerializer<String> valueSerializer() {
    return valueSerializer;
  }

  @Override
  public String name() {
    return loadedValue;
  }

  @Override
  public String configKey() {
    return name();
  }

  @Override
  public TypeLiteral<String> keyType() {
    return new TypeLiteral<String>() {};
  }

  @Override
  public TypeLiteral<String> valueType() {
    return new TypeLiteral<String>() {};
  }

  @Override
  public long maximumWeight() {
    return 0;
  }

  @Override
  public Duration expireAfterWrite() {
    return expireAfterWrite;
  }

  @Override
  public Duration expireFromMemoryAfterAccess() {
    return Duration.ZERO;
  }

  @Override
  public Duration refreshAfterWrite() {
    return refreshAfterWrite;
  }

  @Override
  public Weigher<String, String> weigher() {
    return (s, s2) -> 0;
  }

  @Override
  public CacheLoader<String, String> loader() {
    return new CacheLoader<String, String>() {
      @Override
      public String load(String s) {
        return loadedValue != null ? loadedValue : UUID.randomUUID().toString();
      }
    };
  }
}
