// Copyright (C) 2021 The Android Open Source Project
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

import java.nio.ByteBuffer;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.util.ReadResolvable;
import net.openhft.chronicle.hash.serialization.BytesReader;
import net.openhft.chronicle.hash.serialization.BytesWriter;

public class KeyWrapperMarshaller<V>
    implements BytesWriter<KeyWrapper<V>>,
        BytesReader<KeyWrapper<V>>,
        ReadResolvable<KeyWrapperMarshaller<V>> {

  private final String name;

  KeyWrapperMarshaller(String name) {
    this.name = name;
  }

  @Override
  public KeyWrapperMarshaller<V> readResolve() {
    return new KeyWrapperMarshaller<>(name);
  }

  @Override
  @SuppressWarnings("unchecked")
  public KeyWrapper<V> read(Bytes in, KeyWrapper<V> using) {
    long initialPosition = in.readPosition();

    // Deserialize the length of the serialized value (8 bytes)
    byte[] serializedInt = new byte[Integer.BYTES];
    in.read(serializedInt, 0, Integer.BYTES);
    ByteBuffer buffer2 = ByteBuffer.wrap(serializedInt);
    int vLength = buffer2.getInt(0);
    in.readPosition(initialPosition + Integer.BYTES);

    // Deserialize object V (remaining bytes)
    byte[] serializedV = new byte[vLength];
    in.read(serializedV, 0, vLength);
    V v = (V) CacheSerializers.getKeySerializer(name).deserialize(serializedV);

    using = new KeyWrapper<>(v);

    return using;
  }

  @Override
  public void write(Bytes out, KeyWrapper<V> toWrite) {
    byte[] serialized = CacheSerializers.getKeySerializer(name).serialize(toWrite.getValue());

    // Serialize as follows:
    // length of serialized V | serialized value V
    // 4 bytes                | serialized_length bytes

    int capacity = Integer.BYTES + serialized.length;
    ByteBuffer buffer = ByteBuffer.allocate(capacity);

    buffer.putInt(0, serialized.length);

    buffer.position(Integer.BYTES);
    buffer.put(serialized);

    out.write(buffer.array());
  }
}
