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

  private final CacheSerializer<V> serializer;

  TimedValueMarshaller(CacheSerializer<V> serializer) {
    this.serializer = serializer;
  }

  @Override
  public TimedValueMarshaller<V> readResolve() {
    return new TimedValueMarshaller<>(serializer);
  }

  @Override
  public TimedValue<V> read(Bytes in, TimedValue<V> using) {
    long initialPosition = in.readPosition();

    // Deserialize the version (4 bytes)
    byte[] serializedVersion = new byte[Integer.BYTES];
    in.read(serializedVersion, 0, Integer.BYTES);
    ByteBuffer bufferVersion = ByteBuffer.wrap(serializedVersion);
    int version = bufferVersion.getInt(0);
    in.readPosition(initialPosition + Integer.BYTES);

    // Deserialize the creation timestamp (8 bytes)
    byte[] serializedLong = new byte[Long.BYTES];
    in.read(serializedLong, 0, Long.BYTES);
    ByteBuffer buffer = ByteBuffer.wrap(serializedLong);
    long created = buffer.getLong(0);
    in.readPosition(initialPosition + Integer.BYTES + Long.BYTES);

    // Deserialize the accessed timestamp (8 bytes)
    byte[] serializedAccessed = new byte[Long.BYTES];
    in.read(serializedAccessed, 0, Long.BYTES);
    ByteBuffer bufferAccessed = ByteBuffer.wrap(serializedAccessed);
    long accessed = bufferAccessed.getLong(0);
    in.readPosition(initialPosition + Integer.BYTES + Long.BYTES + Long.BYTES);

    // Deserialize the length of the serialized value (4 bytes)
    byte[] serializedInt = new byte[Integer.BYTES];
    in.read(serializedInt, 0, Integer.BYTES);
    ByteBuffer buffer2 = ByteBuffer.wrap(serializedInt);
    int vLength = buffer2.getInt(0);
    in.readPosition(initialPosition + Integer.BYTES + Long.BYTES + Integer.BYTES + Long.BYTES);

    // Deserialize object V (remaining bytes)
    byte[] serializedV = new byte[vLength];
    in.read(serializedV, 0, vLength);
    V v = serializer.deserialize(serializedV);

    using = new TimedValue<>(v, created, version, accessed);

    return using;
  }

  @Override
  public void write(Bytes out, TimedValue<V> toWrite) {
    byte[] serialized = serializer.serialize(toWrite.getValue());

    // Serialize as follows:
    // version | created | accessed | length of serialized V | serialized value V
    // 4 bytes | 8 bytes |  8 bytes |         4 bytes        | serialized_length bytes

    int capacity = Integer.BYTES + Long.BYTES + Long.BYTES + Integer.BYTES + serialized.length;
    ByteBuffer buffer = ByteBuffer.allocate(capacity);

    int version = toWrite.getVersion();
    buffer.putInt(0, version);
    buffer.position(Integer.BYTES);

    long timestamp = toWrite.getCreated();
    buffer.putLong(timestamp);
    buffer.position(Long.BYTES + Integer.BYTES);

    long accessed = toWrite.getAccessed();
    buffer.putLong(accessed);
    buffer.position(Long.BYTES + Integer.BYTES + Long.BYTES);

    buffer.putInt(serialized.length);

    buffer.position(Long.BYTES + Integer.BYTES + Integer.BYTES + Long.BYTES);
    buffer.put(serialized);

    out.write(buffer.array());
  }
}
