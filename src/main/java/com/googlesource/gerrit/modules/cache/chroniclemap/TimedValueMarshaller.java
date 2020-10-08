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
import com.google.gerrit.server.util.time.TimeUtil;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.util.ReadResolvable;
import net.openhft.chronicle.hash.serialization.BytesReader;
import net.openhft.chronicle.hash.serialization.BytesWriter;

public class TimedValueMarshaller<V>
    implements BytesWriter<CachedValue<V>>,
        BytesReader<CachedValue<V>>,
        ReadResolvable<TimedValueMarshaller<V>> {

  private final CacheSerializer<V> serializer;
  private final Duration expireAfterWrite;
  private final CachedValue<V> INVALID = new Invalid<>();

  TimedValueMarshaller(CacheSerializer<V> serializer, Duration expireAfterWrite) {
    this.serializer = serializer;
    this.expireAfterWrite = expireAfterWrite;
  }

  @Override
  public TimedValueMarshaller<V> readResolve() {
    return new TimedValueMarshaller<>(serializer, expireAfterWrite);
  }

  @Override
  public CachedValue<V> read(Bytes in, CachedValue<V> using) {
    long initialPosition = in.readPosition();

    // Deserialize the creation timestamp (first 8 bytes)
    byte[] serializedLong = new byte[Long.BYTES];
    in.read(serializedLong, 0, Long.BYTES);
    ByteBuffer buffer = ByteBuffer.wrap(serializedLong);
    long created = buffer.getLong(0);
    in.readPosition(initialPosition + Long.BYTES);

    if (expired(created)) {
      return INVALID;
    }

    // Deserialize the length of the serialized value (second 8 bytes)
    byte[] serializedInt = new byte[Integer.BYTES];
    in.read(serializedInt, 0, Integer.BYTES);
    ByteBuffer buffer2 = ByteBuffer.wrap(serializedInt);
    int vLength = buffer2.getInt(0);
    in.readPosition(initialPosition + Long.BYTES + Integer.BYTES);

    // Deserialize object V (remaining bytes)
    byte[] serializedV = new byte[vLength];
    in.read(serializedV, 0, vLength);
    V v = serializer.deserialize(serializedV);

    using = new TimedValue<>(v, created);

    return using;
  }

  @Override
  public void write(Bytes out, CachedValue<V> toWrite) {

    if (toWrite instanceof TimedValue) {
      TimedValue<V> valid = (TimedValue<V>) toWrite;

      byte[] serialized = serializer.serialize(valid.getValue());

      // Serialize as follows:
      // created | length of serialized V | serialized value V
      // 8 bytes |       4 bytes          | serialized_length bytes

      int capacity = Long.BYTES + Integer.BYTES + serialized.length;
      ByteBuffer buffer = ByteBuffer.allocate(capacity);

      long timestamp = valid.getCreated();
      buffer.putLong(0, timestamp);

      buffer.position(Long.BYTES);
      buffer.putInt(serialized.length);

      buffer.position(Long.BYTES + Integer.BYTES);
      buffer.put(serialized);

      out.write(buffer.array());
    }
  }

  private boolean expired(long created) {
    Duration age = Duration.between(Instant.ofEpochMilli(created), TimeUtil.now());
    return !expireAfterWrite.isZero() && age.compareTo(expireAfterWrite) > 0;
  }
}