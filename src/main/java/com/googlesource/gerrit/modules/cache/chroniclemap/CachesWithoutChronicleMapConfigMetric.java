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

import com.codahale.metrics.MetricRegistry;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Singleton
class CachesWithoutChronicleMapConfigMetric {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final MetricMaker metricMaker;
  private final Set<String> uniqueCacheNames;
  private final Map<String, Counter0> cachesWithoutConfig;
  private final Counter0 numberOfCachesWithoutConfig;

  @Inject
  CachesWithoutChronicleMapConfigMetric(MetricMaker metricMaker, MetricRegistry metricRegistry) {
    this.metricMaker = metricMaker;
    this.uniqueCacheNames = Collections.synchronizedSet(new HashSet<>());
    this.cachesWithoutConfig = Collections.synchronizedMap(new HashMap<>());

    String metricName = "cache/chroniclemap/caches_without_chroniclemap_configuration";
    Counter0 metric = (Counter0) metricRegistry.getMetrics().get(metricName);
    this.numberOfCachesWithoutConfig =
        metric != null
            ? metric
            : metricMaker.newCounter(
                metricName,
                new Description(
                        "The number of caches that have no chronicle map configuration provided and fall back to defaults")
                    .setCumulative()
                    .setUnit("caches"));
  }

  void incrementForCache(String name) {
    if (uniqueCacheNames.add(name)) {
      numberOfCachesWithoutConfig.increment();
      Counter0 cacheWithoutMetric =
          cachesWithoutConfig.computeIfAbsent(
              name,
              n -> {
                return metricMaker.newCounter(
                    cacheMetricName(n),
                    new Description(
                            String.format(
                                "Cache %s has no chronicle map configuration provided and fall back to defaults",
                                n))
                        .setCumulative()
                        .setUnit("cache"));
              });
      cacheWithoutMetric.increment();
      logger.atWarning().log("Fall back to default configuration for '%s' cache", name);
    }
  }

  void closeCacheMetric(String name) {
    Optional.ofNullable(cachesWithoutConfig.get(name)).ifPresent(Counter0::remove);
  }

  private String cacheMetricName(String name) {
    return "cache/chroniclemap/cache_without_chroniclemap_configuration_"
        + metricMaker.sanitizeMetricName(name);
  }
}
