/*
 * Copyright 2022-2025 benelog GmbH & Co. KG
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
package io.openepcis.converter.reactive;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import java.io.ByteArrayOutputStream;

/**
 * Utility for chunking byte streams at fixed boundaries for efficient network transmission.
 *
 * <p>This class accumulates bytes and emits them in fixed-size chunks (default 8KB).
 * It's designed to work with reactive streams.
 *
 * <p><strong>Usage with Multi:</strong>
 * <pre>{@code
 * Multi<byte[]> chunkedOutput = sourceMulti
 *     .plug(ByteBufferChunker::chunkBytes);
 * }</pre>
 *
 * <p><strong>Thread safety:</strong> The static {@code chunkBytes()} methods are thread-safe
 * and create isolated state per subscription. Instance methods are NOT thread-safe and
 * should be used from a single thread.
 */
public final class ByteBufferChunker {

  /** Default chunk size of 8KB */
  public static final int DEFAULT_CHUNK_SIZE = 8192;

  private final int chunkSize;
  private final ByteArrayOutputStream buffer;

  /**
   * Creates a new chunker with default 8KB chunk size.
   */
  public ByteBufferChunker() {
    this(DEFAULT_CHUNK_SIZE);
  }

  /**
   * Creates a new chunker with specified chunk size.
   *
   * @param chunkSize the chunk size in bytes
   * @throws IllegalArgumentException if chunkSize is not positive
   */
  public ByteBufferChunker(int chunkSize) {
    if (chunkSize <= 0) {
      throw new IllegalArgumentException("Chunk size must be positive: " + chunkSize);
    }
    this.chunkSize = chunkSize;
    this.buffer = new ByteArrayOutputStream(chunkSize);
  }

  /**
   * Transforms a Multi of byte arrays into chunked output at 8KB boundaries.
   *
   * <p>This is the primary entry point for use with Mutiny's plug operator.
   *
   * @param source the source Multi of byte arrays
   * @return Multi emitting byte arrays in approximately 8KB chunks
   */
  public static Multi<byte[]> chunkBytes(Multi<byte[]> source) {
    return chunkBytes(source, DEFAULT_CHUNK_SIZE);
  }

  /**
   * Transforms a Multi of byte arrays into chunked output at specified boundaries.
   *
   * <p>Each subscription gets its own buffer, making this method safe for concurrent use.
   *
   * @param source the source Multi of byte arrays
   * @param chunkSize the target chunk size in bytes
   * @return Multi emitting byte arrays in approximately chunkSize chunks
   */
  public static Multi<byte[]> chunkBytes(Multi<byte[]> source, int chunkSize) {
    // Use emitter pattern - creates isolated buffer per subscription
    return Multi.createFrom().emitter(emitter -> {
      // Buffer is created fresh for each subscription - no shared state
      ByteArrayOutputStream buffer = new ByteArrayOutputStream(chunkSize);

      source.subscribe().with(
          bytes -> emitChunksToEmitter(bytes, buffer, chunkSize, emitter),
          emitter::fail,
          () -> {
            // Emit any remaining buffered data on completion
            if (buffer.size() > 0) {
              emitter.emit(buffer.toByteArray());
            }
            emitter.complete();
          }
      );
    });
  }

  /**
   * Processes input bytes, emitting complete chunks and buffering remainder.
   */
  private static void emitChunksToEmitter(
      byte[] input,
      ByteArrayOutputStream buffer,
      int chunkSize,
      MultiEmitter<? super byte[]> emitter) {

    int inputOffset = 0;
    int remaining = input.length;

    while (remaining > 0) {
      int bufferSpace = chunkSize - buffer.size();
      int toWrite = Math.min(remaining, bufferSpace);

      buffer.write(input, inputOffset, toWrite);
      inputOffset += toWrite;
      remaining -= toWrite;

      if (buffer.size() >= chunkSize) {
        // Buffer is full, emit chunk
        emitter.emit(buffer.toByteArray());
        buffer.reset();
      }
    }
  }

  /**
   * Adds bytes to the internal buffer and returns any complete chunks.
   *
   * <p>This method is for manual chunking outside of the reactive stream context.
   *
   * @param bytes the bytes to add
   * @return Multi of complete chunks (may be empty if buffer not yet full)
   */
  public Multi<byte[]> add(byte[] bytes) {
    return Multi.createFrom().emitter(emitter -> {
      int inputOffset = 0;
      int remaining = bytes.length;

      while (remaining > 0) {
        int bufferSpace = chunkSize - buffer.size();
        int toWrite = Math.min(remaining, bufferSpace);

        buffer.write(bytes, inputOffset, toWrite);
        inputOffset += toWrite;
        remaining -= toWrite;

        if (buffer.size() >= chunkSize) {
          emitter.emit(buffer.toByteArray());
          buffer.reset();
        }
      }

      emitter.complete();
    });
  }

  /**
   * Flushes any remaining bytes in the buffer.
   *
   * @return the remaining bytes, or empty array if buffer is empty
   */
  public byte[] flush() {
    if (buffer.size() == 0) {
      return new byte[0];
    }
    byte[] result = buffer.toByteArray();
    buffer.reset();
    return result;
  }

  /**
   * Returns the current number of bytes buffered.
   *
   * @return buffered byte count
   */
  public int bufferedSize() {
    return buffer.size();
  }

  /**
   * Resets the internal buffer.
   */
  public void reset() {
    buffer.reset();
  }

  /**
   * Returns the configured chunk size.
   *
   * @return chunk size in bytes
   */
  public int chunkSize() {
    return chunkSize;
  }
}
