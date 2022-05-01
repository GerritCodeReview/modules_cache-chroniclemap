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

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;

public class CacheKeysIndexTest {
  private CacheKeysIndex<String> index;

  @Before
  public void setup() {
    index = new CacheKeysIndex<>();
  }

  @Test
  public void add_shouldUpdateElementPositionWhenAlreadyInSet() {
    index.add("foo", 2L);
    index.add("bar", 1L);
    assertThat(keys(index)).containsExactly("foo", "bar");

    index.add("foo", 1L);
    assertThat(keys(index)).containsExactly("bar", "foo");
  }

  @Test
  public void add_shouldUpdateElementInsertionTimeWhenNewerGetsAdded() {
    index.add("foo", 1L);
    index.add("foo", 2L);

    CacheKeysIndex<String>.TimedKey key = index.keys().iterator().next();
    assertThat(key.getKey()).isEqualTo("foo");
    assertThat(key.getCreated()).isEqualTo(2L);
  }

  @Test
  public void refresh_shouldRefreshInsertionTimeOnRefresh() {
    index.add("foo", 1L);
    index.refresh("foo");

    CacheKeysIndex<String>.TimedKey key = index.keys().iterator().next();
    assertThat(key.getKey()).isEqualTo("foo");
    assertThat(key.getCreated()).isGreaterThan(1L);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void remove_shouldRemoveAndConsumeKeysOlderThan() {
    long than = 5l;
    index.add("newerThan", 10L);
    index.add("olderThan", 2L);
    Consumer<String> consumer = mock(Consumer.class);

    index.removeAndConsumeKeysOlderThan(than, consumer);

    verify(consumer).accept("olderThan");
    assertThat(keys(index)).containsExactly("newerThan");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void remove_shouldReturnFalseIfThereIsNoLruToRemove() {
    Consumer<String> consumer = mock(Consumer.class);

    boolean actual = index.removeAndConsumeLruKey(consumer);

    assertThat(actual).isEqualTo(false);
    verifyNoInteractions(consumer);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void remove_shouldRemoveAndConsumeLruKey() {
    index.add("older", 1L);
    index.add("newer", 1L);
    Consumer<String> consumer = mock(Consumer.class);

    boolean actual = index.removeAndConsumeLruKey(consumer);

    assertThat(actual).isEqualTo(true);
    verify(consumer).accept("older");
    assertThat(keys(index)).containsExactly("newer");
  }

  private static List<String> keys(CacheKeysIndex<String> index) {
    return index.keys().stream().map(CacheKeysIndex.TimedKey::getKey).collect(toList());
  }
}
