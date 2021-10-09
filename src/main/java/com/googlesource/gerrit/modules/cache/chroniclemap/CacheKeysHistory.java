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

import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

class CacheKeysHistory<K> {
  private final ConcurrentLinkedQueue<TimedValue<K>> timedKeys;

  public CacheKeysHistory() {
    timedKeys = new ConcurrentLinkedQueue<>();
  }

  int size() {
    return timedKeys.size();
  }

  boolean isEmpty() {
    return timedKeys.isEmpty();
  }

  void forEachEntry(long maxTimestamp, Consumer<K> action) {
    TimedValue<K> timedKey = timedKeys.peek();
    while (timedKey != null && timedKey.getCreated() < maxTimestamp) {
      action.accept(timedKey.getValue());
      timedKeys.remove();
      timedKey = timedKeys.peek();
    }
  }

  void clear() {
    timedKeys.clear();
  }

  void add(K e) {
    timedKeys.add(new TimedValue<>(e));
  }

  public void addAll(Set<? extends K> keySet) {
    keySet.forEach(this::add);
  }
}
