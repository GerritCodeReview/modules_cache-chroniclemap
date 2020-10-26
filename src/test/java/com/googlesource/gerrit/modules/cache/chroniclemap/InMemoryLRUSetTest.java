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

public class InMemoryLRUSetTest {

  @Test
  public void add_shouldUpdateElementPositionWhenAlreadyInSet() {
    final InMemoryLRUSet<Object> set = new InMemoryLRUSet<>(2);

    set.add("A");
    set.add("B");
    assertThat(set.toArray()).asList().containsExactly("A", "B");

    set.add("A");
    assertThat(set.toArray()).asList().containsExactly("B", "A");
  }

  @Test
  public void add_shouldEvictLRUElement() {
    final InMemoryLRUSet<Object> set = new InMemoryLRUSet<>(2);

    set.add("A");
    set.add("B");
    set.add("C");

    assertThat(set.toArray()).asList().containsExactly("B", "C");
  }
}
