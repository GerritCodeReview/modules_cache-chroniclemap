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

import com.google.common.base.Objects;

public class KeyWrapper<V> {

  private final V value;

  KeyWrapper(V value) {
    this.value = value;
  }

  public V getValue() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof KeyWrapper)) return false;
    KeyWrapper<?> that = (KeyWrapper<?>) o;
    return Objects.equal(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
