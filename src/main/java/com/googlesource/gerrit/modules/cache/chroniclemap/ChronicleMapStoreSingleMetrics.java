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

import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;

class ChronicleMapStoreSingleMetrics extends ChronicleMapStoreMetrics {

  ChronicleMapStoreSingleMetrics(String name, MetricMaker metricMaker) {
    super(name, metricMaker);
  }

  <K, V> void registerCallBackMetrics(ChronicleMapStore<K, V> store) {
    String PERCENTAGE_FREE_SPACE_METRIC =
        "cache/chroniclemap/percentage_free_space_" + sanitizedName;
    String REMAINING_AUTORESIZES_METRIC =
        "cache/chroniclemap/remaining_autoresizes_" + sanitizedName;
    String MAX_AUTORESIZES_METRIC = "cache/chroniclemap/max_autoresizes_" + sanitizedName;

    metricMaker.newCallbackMetric(
        PERCENTAGE_FREE_SPACE_METRIC,
        Long.class,
        new Description(
            String.format("The amount of free space in the %s cache as a percentage", name)),
        () -> (long) store.percentageFreeSpace());

    metricMaker.newCallbackMetric(
        REMAINING_AUTORESIZES_METRIC,
        Integer.class,
        new Description(
            String.format(
                "The number of times the %s cache can automatically expand its capacity", name)),
        store::remainingAutoResizes);

    metricMaker.newConstantMetric(
        MAX_AUTORESIZES_METRIC,
        store.maxAutoResizes(),
        new Description(
            String.format(
                "The maximum number of times the %s cache can automatically expand its capacity",
                name)));
  }
}
