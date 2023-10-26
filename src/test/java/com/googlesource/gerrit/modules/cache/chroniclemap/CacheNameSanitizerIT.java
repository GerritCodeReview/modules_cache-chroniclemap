// Copyright (C) 2023 The Android Open Source Project
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
import static com.googlesource.gerrit.modules.cache.chroniclemap.CacheNameSanitizer.sanitize;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import org.junit.Test;

@UseLocalDisk
public class CacheNameSanitizerIT extends AbstractDaemonTest {
  @Inject MetricMaker metricMaker;

  @Test
  public void shouldNotSanitizeTypicalCacheName() {
    String cacheName = "diff_summary";

    assertThat(sanitize(metricMaker, cacheName)).isEqualTo(cacheName);
  }

  @Test
  public void shouldNotSanitizeCacheNameWithHyphens() {
    String cacheName = "cache_name-with-hyphens";

    assertThat(sanitize(metricMaker, cacheName)).isEqualTo(cacheName);
  }

  @Test
  public void shouldFallbackToMetricMakerSanitization() {
    assertThat(sanitize(metricMaker, "very+confusing.cache#name"))
        .isEqualTo("very_0x2B_confusing_0x2E_cache_0x23_name");
  }
}
