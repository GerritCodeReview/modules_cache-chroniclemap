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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.server.cache.serialize.ObjectIdCacheSerializer;
import java.nio.ByteBuffer;
import net.openhft.chronicle.bytes.Bytes;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class TimedValueMarshallerTest {

  @Test
  public void shouldSerializeAndDeserializeBack() {
    ObjectId id = ObjectId.fromString("1234567890123456789012345678901234567890");
    long timestamp = 1600329018L;
    long accessed = 1601996320L;
    int version = 2;
    TimedValueMarshaller<ObjectId> marshaller =
        new TimedValueMarshaller<>(ObjectIdCacheSerializer.INSTANCE);

    final TimedValue<ObjectId> wrapped = new TimedValue<>(id, timestamp, version, accessed);

    Bytes<ByteBuffer> out = Bytes.elasticByteBuffer();
    marshaller.write(out, wrapped);
    final TimedValue<ObjectId> actual = marshaller.read(out, null);
    assertThat(actual).isEqualTo(wrapped);
  }
}
