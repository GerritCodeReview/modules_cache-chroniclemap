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
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.core.util.ReadResolvable;
import net.openhft.chronicle.hash.serialization.BytesReader;
import net.openhft.chronicle.hash.serialization.BytesWriter;

public class ChronicleMapMarshallerAdapter<T>
    implements BytesWriter<T>, BytesReader<T>, ReadResolvable<ChronicleMapMarshallerAdapter<T>> {

  private final CacheSerializer<T> cacheSerializer;

  ChronicleMapMarshallerAdapter(CacheSerializer<T> cacheSerializer) {
    this.cacheSerializer = cacheSerializer;
  }

  @Override
  public ChronicleMapMarshallerAdapter<T> readResolve() {
    return new ChronicleMapMarshallerAdapter<>(cacheSerializer);
  }

  @Override
  public T read(Bytes in, T using) {
    int serializedLength = (int) in.readUnsignedInt();
    byte[] serialized = new byte[serializedLength];
    in.read(serialized, 0, serializedLength);
    using = cacheSerializer.deserialize(serialized);
    return using;
  }

  @Override
  public void write(Bytes out, T toWrite) {
    final byte[] serialized = cacheSerializer.serialize(toWrite);
    out.writeUnsignedInt(serialized.length);
    out.write(serialized);
  }
}
