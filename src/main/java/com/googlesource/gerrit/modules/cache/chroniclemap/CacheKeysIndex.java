// Copyright (C) 2022 The Android Open Source Project
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

import static java.util.stream.Collectors.toUnmodifiableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.CompatibleWith;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

class CacheKeysIndex<T> {
  /** As opposed to TimedValue keys are equal when their key equals. */
  class TimedKey {
    private final T key;
    private final long created;

    private TimedKey(T key, long created) {
      this.key = key;
      this.created = created;
    }

    T getKey() {
      return key;
    }

    long getCreated() {
      return created;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o instanceof CacheKeysIndex.TimedKey) {
        @SuppressWarnings("unchecked")
        TimedKey other = (TimedKey) o;
        return Objects.equals(key, other.key);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(key);
    }
  }

  private final Set<TimedKey> keys;

  CacheKeysIndex() {
    this.keys = Collections.synchronizedSet(new LinkedHashSet<>());
  }

  @SuppressWarnings("unchecked")
  void add(@CompatibleWith("T") Object key, long created) {
    Objects.requireNonNull(key, "Key value cannot be [null].");
    TimedKey timedKey = new TimedKey((T) key, created);

    // bubble up MRU key by re-adding it to a set
    keys.remove(timedKey);
    keys.add(timedKey);
  }

  void refresh(T key) {
    add(key, System.currentTimeMillis());
  }

  void removeAndConsumeKeysOlderThan(long time, Consumer<T> consumer) {
    Set<TimedKey> toRemoveAndConsume;
    synchronized (keys) {
      toRemoveAndConsume =
          keys.stream().filter(key -> key.created < time).collect(toUnmodifiableSet());
    }
    toRemoveAndConsume.forEach(
        key -> {
          keys.remove(key);
          consumer.accept(key.getKey());
        });
  }

  boolean removeAndConsumeLruKey(Consumer<T> consumer) {
    Optional<TimedKey> lruKey;
    synchronized (keys) {
      lruKey = keys.stream().findFirst();
    }

    return lruKey
        .map(
            key -> {
              keys.remove(key);
              consumer.accept(key.getKey());
              return true;
            })
        .orElse(false);
  }

  void invalidate(@CompatibleWith("T") Object key) {
    keys.remove(key);
  }

  void clear() {
    keys.clear();
  }

  @VisibleForTesting
  Set<TimedKey> keys() {
    return keys;
  }
}
