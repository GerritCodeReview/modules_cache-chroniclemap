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

import static com.google.common.truth.Truth.assertThat;
import static com.googlesource.gerrit.modules.cache.chroniclemap.AssumeJava11.assumeJava11;

import com.google.gerrit.server.cache.serialize.ObjectIdCacheSerializer;
import java.nio.ByteBuffer;
import net.openhft.chronicle.bytes.Bytes;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

public class KeyWrapperMarshallerTest {
  private static final String TEST_CACHE_NAME = "key-wrapper-test";

  @Before
  public void setup() {
    assumeJava11();
    CacheSerializers.registerCacheKeySerializer(TEST_CACHE_NAME, ObjectIdCacheSerializer.INSTANCE);
  }

  @Test
  public void shouldSerializeAndDeserializeBack() {
    ObjectId id = ObjectId.fromString("1234567890123456789012345678901234567890");
    KeyWrapperMarshaller<ObjectId> marshaller = new KeyWrapperMarshaller<>(TEST_CACHE_NAME);

    final KeyWrapper<ObjectId> wrapped = new KeyWrapper<>(id);

    Bytes<ByteBuffer> out = Bytes.elasticByteBuffer();
    marshaller.write(out, wrapped);
    final KeyWrapper<ObjectId> actual = marshaller.read(out, null);
    assertThat(actual).isEqualTo(wrapped);
  }
}
