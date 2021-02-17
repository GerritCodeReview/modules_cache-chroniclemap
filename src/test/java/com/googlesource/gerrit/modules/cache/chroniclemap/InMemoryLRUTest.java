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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class InMemoryLRUTest {

  @Test
  public void add_shouldUpdateElementPositionWhenAlreadyInSet() {
    final InMemoryLRU<Object> map = new InMemoryLRU<>(2);

    map.add("A");
    map.add("B");

    assertThat(map.toArray()).asList().containsExactly("A", "B");

    map.add("A");
    assertThat(map.toArray()).asList().containsExactly("B", "A");
  }

  @Test
  public void add_shouldEvictLRUElement() {
    final InMemoryLRU<Object> map = new InMemoryLRU<>(2);

    map.add("A");
    map.add("B");
    map.add("C");

    assertThat(map.toArray()).asList().containsExactly("B", "C");
  }

  @Test
  public void remove_unexistingEntryShouldReturnFalse() {
    InMemoryLRU<Object> map = new InMemoryLRU<>(2);

    assertThat(map.remove("foo")).isFalse();
  }

  @Test
  public void remove_unexistingEntryShouldReturnTrue() {
    InMemoryLRU<Object> map = new InMemoryLRU<>(2);

    map.add("foo");

    assertThat(map.remove("foo")).isTrue();
  }
}
