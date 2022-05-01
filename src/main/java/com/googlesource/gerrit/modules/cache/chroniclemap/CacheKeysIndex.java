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

import com.google.errorprone.annotations.CompatibleWith;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

class CacheKeysIndex<T> {
  /** In opposite to TimedValue keys are equal when their key equals. */
  private class TimedKey {
    private final T key;
    private final long created;

    private TimedKey(T key, long created) {
      this.key = key;
      this.created = created;
    }

    T getKey() {
      return key;
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

  private final ConcurrentLinkedQueue<TimedKey> keys;

  CacheKeysIndex() {
    this.keys = new ConcurrentLinkedQueue<>();
  }

  @SuppressWarnings("unchecked")
  void add(@CompatibleWith("T") Object key, long created) {
    Objects.requireNonNull(key, "Key value cannot be [null].");
    TimedKey timedKey = new TimedKey((T) key, created);
    // unconditionally remove existing key from the queue so that it gets promoted to MRU when added
    keys.remove(timedKey);
    keys.add(timedKey);
  }

  void refresh(T key) {
    add(key, System.currentTimeMillis());
  }

  void consumeAndRemoveKeysOlderThan(long time, Consumer<T> consumer) {
    keys.stream()
        .filter(key -> key.created < time)
        .forEach(
            key -> {
              consumer.accept(key.key);
              keys.remove(key);
            });
  }

  boolean removeAndConsumeLruKey(Consumer<T> consumer) {
    TimedKey key = keys.poll();
    if (key == null) {
      return false;
    }
    consumer.accept(key.getKey());
    return true;
  }

  void invalidate(@CompatibleWith("T") Object key) {
    keys.remove(key);
  }

  void clear() {
    keys.clear();
  }
}
