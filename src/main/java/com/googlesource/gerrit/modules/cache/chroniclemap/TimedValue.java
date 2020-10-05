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

import com.google.common.base.Objects;

public class TimedValue<V> {

  private final V value;
  private final long created;
  private final int version;

  TimedValue(V value, int version) {
    this.created = System.currentTimeMillis();
    this.value = value;
    this.version = version;
  }

  protected TimedValue(V value, long created, int version) {
    this.created = created;
    this.value = value;
    this.version = version;
  }

  public long getCreated() {
    return created;
  }

  public V getValue() {
    return value;
  }

  public int getVersion() {
    return version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TimedValue)) return false;
    TimedValue<?> that = (TimedValue<?>) o;
    return created == that.created && version == that.version && Objects.equal(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value, version, created);
  }
}
