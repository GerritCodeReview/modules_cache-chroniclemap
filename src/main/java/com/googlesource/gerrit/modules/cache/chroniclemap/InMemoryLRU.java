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

public class InMemoryLRU<K> {

  private final Map<K, Boolean> LRUMap;

  private static final Boolean dummyValue = Boolean.TRUE;
  private final int capacity;

  public InMemoryLRU(int capacity) {
    this.capacity = capacity;

    LRUMap =
        Collections.synchronizedMap(
            new LinkedHashMap<K, Boolean>(capacity, 0.75f, true) {
              @Override
              protected boolean removeEldestEntry(Map.Entry<K, Boolean> eldest) {
                return size() > capacity;
              }
            });
  }

  public void add(K objKey) {
    LRUMap.putIfAbsent(objKey, dummyValue);
  }

  public boolean contains(K key) {
    return LRUMap.containsKey(key);
  }

  public boolean remove(K key) {
    return LRUMap.remove(key);
  }

  public void invalidateAll() {
    LRUMap.clear();
  }

  public int size() {
    return LRUMap.size();
  }

  @VisibleForTesting
  protected Object[] toArray() {
    return LRUMap.keySet().toArray();
  }

  public int getCapacity() {
    return capacity;
  }
}
