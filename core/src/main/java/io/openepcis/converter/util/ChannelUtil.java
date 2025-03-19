/*
 * Copyright 2022-2024 benelog GmbH & Co. KG
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package io.openepcis.converter.util;

import io.smallrye.mutiny.Multi;
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
    try (final ReadableByteChannel src = Channels.newChannel(inputStream);
        final WritableByteChannel dest = Channels.newChannel(outputStream); ) {
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
    }
  }

  public static Multi<byte[]> toMulti(InputStream inputStream) {
    return Multi.createFrom()
        .emitter(
            em -> {
              try (final ReadableByteChannel src = Channels.newChannel(inputStream); ) {
                byte[] bytes;
                final ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
                while (src.read(buffer) != -1) {
                  buffer.flip();
                  bytes = new byte[buffer.remaining()];
                  buffer.get(bytes, 0, bytes.length);
                  em.emit(bytes);
                  buffer.compact();
                }
                buffer.flip();
                while (buffer.hasRemaining()) {
                  bytes = new byte[buffer.remaining()];
                  buffer.get(bytes, 0, bytes.length);
                  em.emit(bytes);
                }
                em.complete();
              } catch (Exception e) {
                em.fail(e);
              }
            });
  }
}
