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

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class H2AggregateData {
  protected abstract String cacheName();

  protected abstract long size();

  protected abstract long avgKeySize();

  protected abstract long avgValueSize();

  protected static H2AggregateData create(
      String cacheName, long size, long avgKeySize, long avgValueSize) {
    return new AutoValue_H2AggregateData(cacheName, size, avgKeySize, avgValueSize);
  }

  protected static H2AggregateData empty(String cacheName) {
    return new AutoValue_H2AggregateData(cacheName, 0L, 0L, 0L);
  }

  protected boolean isEmpty() {
    return size() == 0L;
  }
}
