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

import com.google.gerrit.server.cache.serialize.CacheSerializer;
import java.nio.ByteBuffer;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.util.ReadResolvable;
import net.openhft.chronicle.hash.serialization.BytesReader;
import net.openhft.chronicle.hash.serialization.BytesWriter;

public class TimedValueMarshaller<V>
    implements BytesWriter<TimedValue<V>>,
        BytesReader<TimedValue<V>>,
        ReadResolvable<TimedValueMarshaller<V>> {

  private static final ThreadLocal<byte[]> staticBuffer =
      new ThreadLocal<byte[]>() {
        @Override
        protected byte[] initialValue() {
          return new byte[Long.BYTES + Integer.BYTES];
        }
      };

  private final String name;
  private final CacheSerializer<V> cacheSerializer;

  TimedValueMarshaller(String name) {
    this.name = name;
    this.cacheSerializer = CacheSerializers.getValueSerializer(name);
  }

  @Override
  public TimedValueMarshaller<V> readResolve() {
    return new TimedValueMarshaller<>(name);
  }

  @SuppressWarnings("rawtypes")
  @Override
  public TimedValue<V> read(Bytes in, TimedValue<V> using) {
    byte[] bytesBuffer = staticBuffer.get();
    in.read(bytesBuffer);
    ByteBuffer buffer = ByteBuffer.wrap(bytesBuffer);
    long created = buffer.getLong();
    int vLength = buffer.getInt();

    // Deserialize object V (remaining bytes)
    byte[] serializedV = new byte[vLength];
    in.read(serializedV);
    V v = cacheSerializer.deserialize(serializedV);

    return new TimedValue<>(v, created);
  }

  @SuppressWarnings("rawtypes")
  @Override
  public void write(Bytes out, TimedValue<V> toWrite) {
    byte[] serialized = cacheSerializer.serialize(toWrite.getValue());

    // Serialize as follows:
    // created | length of serialized V | serialized value V
    // 8 bytes |       4 bytes          | serialized_length bytes
    byte[] bytesBuffer = staticBuffer.get();
    ByteBuffer buffer = ByteBuffer.wrap(bytesBuffer);
    buffer.putLong(toWrite.getCreated());
    buffer.putInt(serialized.length);
    out.write(bytesBuffer);
    out.write(serialized);
  }
}
