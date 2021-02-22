package com.googlesource.gerrit.modules.cache.chroniclemap;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.hash.serialization.BytesReader;
import net.openhft.chronicle.hash.serialization.BytesWriter;

import java.nio.ByteBuffer;

public class TimedValueChronicleMaBinaryWriter<V>
    implements BytesWriter<TimedValue<V>>, BytesReader<TimedValue<V>> {

  @Override
  public TimedValue<V> read(Bytes in, TimedValue<V> using) {
    return using;
  }

  private byte[] unbox(Byte[] bytesWrapper) {
    byte[] bytes = new byte[bytesWrapper.length];
    for (int j = 0; j < bytesWrapper.length; j++) bytes[j] = bytesWrapper[j];
    return bytes;
  }

  @Override
  public void write(Bytes out, TimedValue<V> toWrite) {
    byte[] serialized = unbox((Byte[]) toWrite.getValue());

    // Serialize as follows:
    // created | length of serialized V | serialized value V
    // 8 bytes |       4 bytes          | serialized_length bytes

    int capacity = Long.BYTES + Integer.BYTES + serialized.length;
    ByteBuffer buffer = ByteBuffer.allocate(capacity);

    long timestamp = toWrite.getCreated();
    buffer.putLong(0, timestamp);

    buffer.position(Long.BYTES);
    buffer.putInt(serialized.length);

    buffer.position(Long.BYTES + Integer.BYTES);
    buffer.put(serialized);

    out.write(buffer.array());
  }
}
