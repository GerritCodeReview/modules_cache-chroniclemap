package com.googlesource.gerrit.modules.cache.chroniclemap;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.hash.serialization.BytesReader;
import net.openhft.chronicle.hash.serialization.BytesWriter;

public class ChronicleMapBinaryWriter<K> implements BytesWriter<K>, BytesReader<K> {

  @Override
  public void write(Bytes out, K toWrite) {
    final Byte[] bytesToWrite = (Byte[]) toWrite;
    out.writeUnsignedInt(bytesToWrite.length);
    out.write(unbox(bytesToWrite));
  }

  private byte[] unbox(Byte[] bytesWrapper) {
    byte[] bytes = new byte[bytesWrapper.length];
    for (int j = 0; j < bytesWrapper.length; j++) bytes[j] = bytesWrapper[j];
    return bytes;
  }

  @Override
  public K read(Bytes in, K using) {
    //XXX: reads should never been used with this marshaller
    return using;
  }
}
