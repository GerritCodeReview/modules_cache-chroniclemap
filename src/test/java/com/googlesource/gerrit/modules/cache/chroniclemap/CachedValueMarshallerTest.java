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
import java.time.Duration;

import net.openhft.chronicle.bytes.Bytes;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class CachedValueMarshallerTest {

  @Test
  public void shouldSerializeAndDeserializeBackWhenValueHasNotExpired() {
    ObjectId id = ObjectId.fromString("1234567890123456789012345678901234567890");
    long timestamp = 1600329018L;
    CachedValueMarshaller<ObjectId> marshaller =
        new CachedValueMarshaller<>(ObjectIdCacheSerializer.INSTANCE, Duration.ZERO);

    final TimedValue<ObjectId> wrapped = new TimedValue<>(id, timestamp);

    Bytes<ByteBuffer> out = Bytes.elasticByteBuffer();
    marshaller.write(out, wrapped);
    final CachedValue<ObjectId> actual = marshaller.read(out, null);
    assertThat(actual).isEqualTo(wrapped);
  }

  @Test
  public void shouldSerializeAndDeserializeBackWhenValueHasExpired() throws InterruptedException {
    ObjectId id = ObjectId.fromString("1234567890123456789012345678901234567890");
    CachedValueMarshaller<ObjectId> marshaller =
            new CachedValueMarshaller<>(ObjectIdCacheSerializer.INSTANCE, Duration.ofSeconds(1));

    final TimedValue<ObjectId> wrapped = new TimedValue<>(id);
    Thread.sleep(1001); //Allow value to expire

    Bytes<ByteBuffer> out = Bytes.elasticByteBuffer();
    marshaller.write(out, wrapped);
    final CachedValue<ObjectId> actual = marshaller.read(out, null);
    assertThat(actual).isEqualTo(new Invalid<ObjectId>());
  }
}
