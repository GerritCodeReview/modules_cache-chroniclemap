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

import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.util.ReadResolvable;
import net.openhft.chronicle.hash.serialization.BytesReader;
import net.openhft.chronicle.hash.serialization.BytesWriter;

public class TimedValueMarshaller<V> extends SerializationMetricsForCache
    implements BytesWriter<TimedValue<V>>,
        BytesReader<TimedValue<V>>,
        ReadResolvable<TimedValueMarshaller<V>> {

  private final CacheSerializer<V> cacheSerializer;

  TimedValueMarshaller(MetricMaker metricMaker, String name) {
    super(metricMaker, name);
    this.cacheSerializer = CacheSerializers.getValueSerializer(name);
  }

  private TimedValueMarshaller(Metrics metrics, String name) {
    super(metrics, name);
    this.cacheSerializer = CacheSerializers.getValueSerializer(name);
  }

  @Override
  public TimedValueMarshaller<V> readResolve() {
    return new TimedValueMarshaller<>(metrics, name);
  }

  @SuppressWarnings("rawtypes")
  @Override
  public TimedValue<V> read(Bytes in, TimedValue<V> using) {
    try (Timer0.Context timer = metrics.deserializeLatency.start()) {
      // Deserialize the creation timestamp (first 8 bytes)
      long created = in.readLong();

      // Deserialize the length of the serialized value (second 4 bytes)
      int vLength = (int) in.readUnsignedInt();

      // Deserialize object V (remaining bytes)
      byte[] serializedV = new byte[vLength];
      in.read(serializedV, 0, vLength);
      V v = cacheSerializer.deserialize(serializedV);

      if (using == null) {
        using = new TimedValue<>(v, created);
      } else {
        using.setCreated(created);
        using.setValue(v);
      }
      return using;
    }
  }

  @SuppressWarnings("rawtypes")
  @Override
  public void write(Bytes out, TimedValue<V> toWrite) {
    try (Timer0.Context timer = metrics.serializeLatency.start()) {
      byte[] serialized = cacheSerializer.serialize(toWrite.getValue());

      // Serialize as follows:
      // created | length of serialized V | serialized value V
      // 8 bytes |       4 bytes          | serialized_length bytes
      out.writeLong(toWrite.getCreated());
      out.writeUnsignedInt(serialized.length);
      out.write(serialized);
    }
  }
}
