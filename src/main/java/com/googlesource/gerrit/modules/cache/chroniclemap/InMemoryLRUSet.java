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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class InMemoryLRUSet<K> {

  private final Set<K> set;

  public InMemoryLRUSet(long capacity) {
    this.set =
        Collections.newSetFromMap(
            new LinkedHashMap<K, Boolean>() {
              @Override
              protected boolean removeEldestEntry(Map.Entry<K, Boolean> eldest) {
                return size() > capacity;
              }
            });
  }

  public void add(K objKey) {
    // We need to remove and insert it so that it will always take
    // the correct relative position
    if (set.contains(objKey)) {
      set.remove(objKey);
    }
    set.add(objKey);
  }

  public boolean contains(K key) {
    return set.contains(key);
  }

  public boolean remove(K key) {
    return set.remove(key);
  }

  public void invalidateAll() {
    set.clear();
  }

  @VisibleForTesting
  protected Object[] toArray() {
    return set.toArray();
  }
}
