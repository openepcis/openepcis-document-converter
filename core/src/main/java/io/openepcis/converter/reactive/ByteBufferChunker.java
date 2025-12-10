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
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Utility for chunking byte streams at fixed boundaries for efficient network transmission.
 *
 * <p>This class delegates to {@link io.openepcis.reactive.util.ByteBufferChunker} in the
 * openepcis-reactive-event-publisher module and provides backward compatibility for
 * existing code using this class.
 *
 * <p>For new code, consider using {@link io.openepcis.reactive.util.ByteBufferChunker} directly.
 *
 * @see io.openepcis.reactive.util.ByteBufferChunker
 * @deprecated Use {@link io.openepcis.reactive.util.ByteBufferChunker} instead
 */
@Deprecated(since = "999-SNAPSHOT", forRemoval = false)
public final class ByteBufferChunker {

  /** Default chunk size of 8KB */
  public static final int DEFAULT_CHUNK_SIZE =
      io.openepcis.reactive.util.ByteBufferChunker.DEFAULT_CHUNK_SIZE;

  private final io.openepcis.reactive.util.ByteBufferChunker delegate;

  /**
   * Creates a new chunker with default 8KB chunk size.
   */
  public ByteBufferChunker() {
    this.delegate = new io.openepcis.reactive.util.ByteBufferChunker();
  }

  /**
   * Creates a new chunker with specified chunk size.
   *
   * @param chunkSize the chunk size in bytes
   * @throws IllegalArgumentException if chunkSize is not positive
   */
  public ByteBufferChunker(int chunkSize) {
    this.delegate = new io.openepcis.reactive.util.ByteBufferChunker(chunkSize);
  }

  // ==================== Synchronous Chunking ====================

  /**
   * Chunks a byte array into fixed-size chunks.
   *
   * @param data the byte array to chunk
   * @param chunkSize the maximum size of each chunk
   * @return list of byte array chunks
   * @throws IllegalArgumentException if chunkSize is not positive
   */
  public static List<byte[]> chunk(byte[] data, int chunkSize) {
    return io.openepcis.reactive.util.ByteBufferChunker.chunk(data, chunkSize);
  }

  /**
   * Chunks a byte array into default 8KB chunks.
   *
   * @param data the byte array to chunk
   * @return list of byte array chunks
   */
  public static List<byte[]> chunk(byte[] data) {
    return io.openepcis.reactive.util.ByteBufferChunker.chunk(data);
  }

  // ==================== Reactive Chunking: Multi<byte[]> ====================

  /**
   * Transforms a Multi of byte arrays into chunked output at 8KB boundaries.
   *
   * @param source the source Multi of byte arrays
   * @return Multi emitting byte arrays in approximately 8KB chunks
   */
  public static Multi<byte[]> chunkBytes(Multi<byte[]> source) {
    return io.openepcis.reactive.util.ByteBufferChunker.chunkBytes(source);
  }

  /**
   * Transforms a Multi of byte arrays into chunked output at specified boundaries.
   *
   * @param source the source Multi of byte arrays
   * @param chunkSize the target chunk size in bytes
   * @return Multi emitting byte arrays in approximately chunkSize chunks
   */
  public static Multi<byte[]> chunkBytes(Multi<byte[]> source, int chunkSize) {
    return io.openepcis.reactive.util.ByteBufferChunker.chunkBytes(source, chunkSize);
  }

  // ==================== Reactive Conversion: Multi<ByteBuffer> ====================

  /**
   * Converts a Multi of byte arrays to a Multi of ByteBuffers.
   *
   * @param source the source Multi emitting byte arrays
   * @return Multi emitting ByteBuffers
   */
  public static Multi<ByteBuffer> toByteBuffers(Multi<byte[]> source) {
    return io.openepcis.reactive.util.ByteBufferChunker.toByteBuffers(source);
  }

  /**
   * Converts a Multi of ByteBuffers to a Multi of byte arrays.
   *
   * @param source the source Multi emitting ByteBuffers
   * @return Multi emitting byte arrays
   */
  public static Multi<byte[]> fromByteBuffers(Multi<ByteBuffer> source) {
    return io.openepcis.reactive.util.ByteBufferChunker.fromByteBuffers(source);
  }

  /**
   * Chunks a Multi of ByteBuffers into fixed-size byte array chunks.
   *
   * @param source the source Multi emitting ByteBuffers
   * @param chunkSize the target chunk size
   * @return Multi emitting fixed-size byte array chunks
   */
  public static Multi<byte[]> chunkByteBuffers(Multi<ByteBuffer> source, int chunkSize) {
    return io.openepcis.reactive.util.ByteBufferChunker.chunkByteBuffers(source, chunkSize);
  }

  /**
   * Chunks a Multi of ByteBuffers into default 8KB byte array chunks.
   *
   * @param source the source Multi emitting ByteBuffers
   * @return Multi emitting 8KB byte array chunks
   */
  public static Multi<byte[]> chunkByteBuffers(Multi<ByteBuffer> source) {
    return io.openepcis.reactive.util.ByteBufferChunker.chunkByteBuffers(source);
  }

  /**
   * Chunks and converts a Multi of ByteBuffers to chunked ByteBuffers.
   *
   * @param source the source Multi emitting ByteBuffers
   * @param chunkSize the target chunk size
   * @return Multi emitting fixed-size ByteBuffers
   */
  public static Multi<ByteBuffer> chunkToByteBuffers(Multi<ByteBuffer> source, int chunkSize) {
    return io.openepcis.reactive.util.ByteBufferChunker.chunkToByteBuffers(source, chunkSize);
  }

  /**
   * Chunks and converts a Multi of ByteBuffers to default 8KB chunked ByteBuffers.
   *
   * @param source the source Multi emitting ByteBuffers
   * @return Multi emitting 8KB ByteBuffers
   */
  public static Multi<ByteBuffer> chunkToByteBuffers(Multi<ByteBuffer> source) {
    return io.openepcis.reactive.util.ByteBufferChunker.chunkToByteBuffers(source);
  }

  // ==================== Instance Methods ====================

  /**
   * Adds bytes to the internal buffer and returns any complete chunks.
   *
   * @param bytes the bytes to add
   * @return Multi of complete chunks (may be empty if buffer not yet full)
   */
  public Multi<byte[]> add(byte[] bytes) {
    return delegate.add(bytes);
  }

  /**
   * Flushes any remaining bytes in the buffer.
   *
   * @return the remaining bytes, or empty array if buffer is empty
   */
  public byte[] flush() {
    return delegate.flush();
  }

  /**
   * Returns the current number of bytes buffered.
   *
   * @return buffered byte count
   */
  public int bufferedSize() {
    return delegate.bufferedSize();
  }

  /**
   * Resets the internal buffer.
   */
  public void reset() {
    delegate.reset();
  }

  /**
   * Returns the configured chunk size.
   *
   * @return chunk size in bytes
   */
  public int chunkSize() {
    return delegate.chunkSize();
  }

  // ==================== Netty ByteBuf Support ====================

  /**
   * Chunks and converts a Multi of byte arrays to Netty ByteBufs.
   *
   * @param source the source Multi emitting byte arrays
   * @param chunkSize the target chunk size
   * @return Multi emitting Netty ByteBufs
   * @throws IllegalStateException if Netty is not available
   */
  public static Multi<io.netty.buffer.ByteBuf> chunkToNettyBuffers(
      Multi<byte[]> source, int chunkSize) {
    return io.openepcis.reactive.util.ByteBufferChunker.chunkToNettyBuffers(source, chunkSize);
  }

  /**
   * Chunks and converts a Multi of byte arrays to default 8KB Netty ByteBufs.
   *
   * @param source the source Multi emitting byte arrays
   * @return Multi emitting 8KB Netty ByteBufs
   * @throws IllegalStateException if Netty is not available
   */
  public static Multi<io.netty.buffer.ByteBuf> chunkToNettyBuffers(Multi<byte[]> source) {
    return io.openepcis.reactive.util.ByteBufferChunker.chunkToNettyBuffers(source);
  }

  /**
   * Chunks and converts a Multi of byte arrays to pooled Netty ByteBufs.
   *
   * @param source the source Multi emitting byte arrays
   * @param chunkSize the target chunk size
   * @return Multi emitting pooled Netty ByteBufs
   * @throws IllegalStateException if Netty is not available
   */
  public static Multi<io.netty.buffer.ByteBuf> chunkToPooledNettyBuffers(
      Multi<byte[]> source, int chunkSize) {
    return io.openepcis.reactive.util.ByteBufferChunker.chunkToPooledNettyBuffers(source, chunkSize);
  }

  /**
   * Chunks and converts a Multi of byte arrays to default 8KB pooled Netty ByteBufs.
   *
   * @param source the source Multi emitting byte arrays
   * @return Multi emitting 8KB pooled Netty ByteBufs
   * @throws IllegalStateException if Netty is not available
   */
  public static Multi<io.netty.buffer.ByteBuf> chunkToPooledNettyBuffers(Multi<byte[]> source) {
    return io.openepcis.reactive.util.ByteBufferChunker.chunkToPooledNettyBuffers(source);
  }
}
