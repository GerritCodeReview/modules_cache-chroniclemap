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
// limitations under the License.
// See the License for the specific language governing permissions and
package com.googlesource.gerrit.modules.cache.chroniclemap;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.server.cache.serialize.ObjectIdCacheSerializer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.openhft.chronicle.bytes.Bytes;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class ChronicleMapMarshallerAdapterTest {

  @Test
  public void shouldDeserializeToTheSameObject() {
    ObjectId id = ObjectId.fromString("aabbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    byte[] serializedId = ObjectIdCacheSerializer.INSTANCE.serialize(id);
    ByteBuffer serializedIdWithLen = ByteBuffer.allocate(serializedId.length + 4);
    serializedIdWithLen.order(ByteOrder.LITTLE_ENDIAN);
    serializedIdWithLen.putInt(serializedId.length);
    serializedIdWithLen.put(serializedId);

    ChronicleMapMarshallerAdapter<ObjectId> marshaller =
        new ChronicleMapMarshallerAdapter<>(ObjectIdCacheSerializer.INSTANCE);
    ObjectId out = marshaller.read(Bytes.allocateDirect(serializedIdWithLen.array()), null);

    assertThat(id).isEqualTo(out);
  }

  @Test
  public void shouldSerializeToTheSameObject() {
    ObjectId id = ObjectId.fromString("aabbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    ChronicleMapMarshallerAdapter<ObjectId> marshaller =
        new ChronicleMapMarshallerAdapter<>(ObjectIdCacheSerializer.INSTANCE);

    Bytes<ByteBuffer> out = Bytes.elasticByteBuffer();
    marshaller.write(out, id);
    assertThat(marshaller.read(out, null)).isEqualTo(id);
  }
}
