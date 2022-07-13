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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import java.nio.ByteBuffer;
import java.util.Optional;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.util.ReadResolvable;
import net.openhft.chronicle.hash.serialization.BytesReader;
import net.openhft.chronicle.hash.serialization.BytesWriter;

public class TimedValueMarshaller<V>
    implements BytesWriter<TimedValue<V>>,
        BytesReader<TimedValue<V>>,
        ReadResolvable<TimedValueMarshaller<V>> {

  private static class Metrics {
    private final Timer0 deserializeLatency;
    private final Timer0 serializeLatency;

    private Metrics(MetricMaker metricMaker, String name) {
      String sanitizedName = metricMaker.sanitizeMetricName(name);

      deserializeLatency =
          metricMaker.newTimer(
              "cache/chroniclemap/store_deserialize_latency_" + sanitizedName,
              new Description(
                      String.format(
                          "The latency of deserializing entries from chronicle-map store for %s"
                              + " cache",
                          name))
                  .setCumulative()
                  .setUnit(Units.NANOSECONDS));

      serializeLatency =
          metricMaker.newTimer(
              "cache/chroniclemap/store_serialize_latency_" + sanitizedName,
              new Description(
                      String.format(
                          "The latency of serializing entries to chronicle-map store for %s cache",
                          name))
                  .setCumulative()
                  .setUnit(Units.NANOSECONDS));
    }
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String name;
  private final transient Metrics metrics;

  TimedValueMarshaller(MetricMaker metricMaker, String name) {
    this(new Metrics(metricMaker, name), name);
  }

  private TimedValueMarshaller(Metrics metrics, String name) {
    this.metrics = metrics;
    this.name = name;
  }

  @Override
  public TimedValueMarshaller<V> readResolve() {
    Metrics toCopy =
        Optional.ofNullable(metrics)
            .orElseGet(
                () -> {
                  logger.atSevere().log(
                      "DisabledMetricMaker is used to create metrics for '%s' cache. That shouldn't happen outside the tests.",
                      name);
                  return new Metrics(new DisabledMetricMaker(), name);
                });
    return new TimedValueMarshaller<>(toCopy, name);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public TimedValue<V> read(Bytes in, TimedValue<V> using) {
    try (Timer0.Context timer = metrics.deserializeLatency.start()) {
      long initialPosition = in.readPosition();

      // Deserialize the creation timestamp (first 8 bytes)
      byte[] serializedLong = new byte[Long.BYTES];
      in.read(serializedLong, 0, Long.BYTES);
      ByteBuffer buffer = ByteBuffer.wrap(serializedLong);
      long created = buffer.getLong(0);
      in.readPosition(initialPosition + Long.BYTES);

      // Deserialize the length of the serialized value (second 8 bytes)
      byte[] serializedInt = new byte[Integer.BYTES];
      in.read(serializedInt, 0, Integer.BYTES);
      ByteBuffer buffer2 = ByteBuffer.wrap(serializedInt);
      int vLength = buffer2.getInt(0);
      in.readPosition(initialPosition + Long.BYTES + Integer.BYTES);

      // Deserialize object V (remaining bytes)
      byte[] serializedV = new byte[vLength];
      in.read(serializedV, 0, vLength);
      V v = (V) CacheSerializers.getValueSerializer(name).deserialize(serializedV);

      using = new TimedValue<>(v, created);

      return using;
    }
  }

  @SuppressWarnings("rawtypes")
  @Override
  public void write(Bytes out, TimedValue<V> toWrite) {
    try (Timer0.Context timer = metrics.serializeLatency.start()) {
      byte[] serialized = CacheSerializers.getValueSerializer(name).serialize(toWrite.getValue());

      // Serialize as follows:
      // created | length of serialized V | serialized value V
      // 8 bytes |       4 bytes          | serialized_length bytes

      int capacity = Long.BYTES + Integer.BYTES + serialized.length;
      ByteBuffer buffer = ByteBuffer.allocate(capacity);

      long timestamp = toWrite.getCreated();
      buffer.putLong(0, timestamp);

      buffer.position(Long.BYTES);
      buffer.putInt(serialized.length);

      buffer.position(Long.BYTES + Integer.BYTES);
      buffer.put(serialized);

      out.write(buffer.array());
    }
  }
}
