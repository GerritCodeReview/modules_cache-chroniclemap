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
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class CommonValueMarshaller {
  protected static class Metrics {
    final Timer0 deserializeLatency;
    final Timer0 serializeLatency;

    Metrics(MetricMaker metricMaker, String cacheName) {
      String sanitizedName = metricMaker.sanitizeMetricName(cacheName);

      deserializeLatency =
          metricMaker.newTimer(
              "cache/chroniclemap/store_deserialize_latency_" + sanitizedName,
              new Description(
                      String.format(
                          "The latency of deserializing entries from chronicle-map store for %s"
                              + " cache",
                          cacheName))
                  .setCumulative()
                  .setUnit(Units.NANOSECONDS));

      serializeLatency =
          metricMaker.newTimer(
              "cache/chroniclemap/store_serialize_latency_" + sanitizedName,
              new Description(
                      String.format(
                          "The latency of serializing entries to chronicle-map store for %s cache",
                          cacheName))
                  .setCumulative()
                  .setUnit(Units.NANOSECONDS));
    }
  }

  /**
   * Metrics contains relations with objects created upon Gerrit start and as such it cannot be
   * serialized. Upon the situation when store is being restored from a persistent file Marshaller
   * gets deserialised and its transient metrics value needs to be re-initialised with object that
   * was created upon Gerrit the start. Note that Marshaller creation is a step the prepends the
   * cache build therefore it is ensured that metrics object is always available for cache's
   * Marshaller.
   */
  protected static final Map<String, Metrics> metricsCache = new HashMap<>();

  protected final String name;
  protected final transient Metrics metrics;

  protected CommonValueMarshaller(MetricMaker metricMaker, String name) {
    this(createMetrics(metricMaker, name), name);
  }

  protected CommonValueMarshaller(Metrics metrics, String name) {
    this.metrics = Optional.ofNullable(metrics).orElseGet(() -> metricsCache.get(name));
    this.name = name;
  }

  protected static Metrics createMetrics(MetricMaker metricMaker, String name) {
    Metrics metrics = new Metrics(metricMaker, name);
    metricsCache.put(name, metrics);
    return metrics;
  }
}
