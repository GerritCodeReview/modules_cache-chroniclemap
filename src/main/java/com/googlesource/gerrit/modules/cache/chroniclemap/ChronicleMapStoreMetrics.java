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

import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import java.util.Optional;

abstract class ChronicleMapStoreMetrics {
  protected final Optional<String> name;
  protected final MetricMaker metricMaker;
  protected final String sanitizedName;

  private final Counter0 storePutFailures;

  protected ChronicleMapStoreMetrics(MetricMaker metricMaker) {
    this(Optional.empty(), metricMaker);
  }

  protected ChronicleMapStoreMetrics(String name, MetricMaker metricMaker) {
    this(Optional.of(name), metricMaker);
  }

  private ChronicleMapStoreMetrics(Optional<String> name, MetricMaker metricMaker) {
    this.name = name;
    Optional<String> sanitizedMetricName = name.map(metricMaker::sanitizeMetricName);
    this.sanitizedName = sanitizedMetricName.orElse("");
    this.metricMaker = metricMaker;

    this.storePutFailures =
        metricMaker.newCounter(
            "cache/chroniclemap/store_put_failures"
                + sanitizedMetricName.map("_"::concat).orElse(""),
            new Description(
                    "The number of errors caught when inserting entries in chronicle-map store: "
                        + name)
                .setCumulative()
                .setUnit("errors"));
  }

  void incrementPutFailures() {
    storePutFailures.increment();
  }
}
