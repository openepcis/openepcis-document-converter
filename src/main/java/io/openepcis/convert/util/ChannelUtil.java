package io.openepcis.convert.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ChannelUtil {

  public static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
    final ReadableByteChannel src = Channels.newChannel(inputStream);
    final WritableByteChannel dest = Channels.newChannel(outputStream);
    try {
      final ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
      while (src.read(buffer) != -1) {
        buffer.flip();
        dest.write(buffer);
        buffer.compact();
      }
      buffer.flip();
      while (buffer.hasRemaining()) {
        dest.write(buffer);
      }
    } finally {
      dest.close();
    }
  }
}
