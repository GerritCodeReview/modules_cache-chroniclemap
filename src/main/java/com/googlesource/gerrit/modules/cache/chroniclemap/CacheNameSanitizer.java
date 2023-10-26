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

import com.google.gerrit.metrics.MetricMaker;
import java.util.regex.Pattern;

class CacheNameSanitizer {
  private static final Pattern METRIC_NAME_PATTERN =
      Pattern.compile("[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*");

  /**
   * Detect if <code>cacheName</code> contains only letters/digits and `-` sign (typical cache name)
   * and replace `-` with `_` in all other cases call {@link MetricMaker#sanitizeMetricName(String)}
   * to sanitize the name. This way existing dashboards should work.
   */
  static String sanitize(MetricMaker metricMaker, String cacheName) {
    if (METRIC_NAME_PATTERN.matcher(cacheName).matches()) {
      return cacheName;
    }
    return metricMaker.sanitizeMetricName(cacheName);
  }

  private CacheNameSanitizer() {}
}
