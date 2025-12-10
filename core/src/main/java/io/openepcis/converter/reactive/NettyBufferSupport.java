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

/**
 * Optional Netty ByteBuf support for zero-copy buffer operations.
 *
 * <p>This class provides more efficient buffer management for high-throughput scenarios
 * by leveraging Netty's pooled buffer allocator and zero-copy operations.
 *
 * <p><strong>Availability:</strong> This class only works if Netty is on the classpath.
 * Check availability via {@link #isAvailable()} before using Netty-specific methods.
 *
 * <p><strong>To enable, add netty-buffer to your dependencies:</strong>
 * <pre>{@code
 * <dependency>
 *     <groupId>io.netty</groupId>
 *     <artifactId>netty-buffer</artifactId>
 * </dependency>
 * }</pre>
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * if (NettyBufferSupport.isAvailable()) {
 *     Multi<io.netty.buffer.ByteBuf> nettyStream =
 *         NettyBufferSupport.toNettyBuffers(transformer.convert(source, conversion));
 *
 *     nettyStream.subscribe().with(
 *         buf -> {
 *             try {
 *                 // Process ByteBuf directly without copying
 *                 channel.write(buf);
 *             } finally {
 *                 buf.release();  // IMPORTANT: Release to prevent leaks
 *             }
 *         });
 * }
 * }</pre>
 *
 * <p><strong>Important:</strong> When using Netty ByteBufs, consumers MUST call
 * {@code ByteBuf.release()} on each buffer to prevent memory leaks.
 *
 * @see ReactiveVersionTransformer
 */
public final class NettyBufferSupport {

  private static final boolean NETTY_AVAILABLE = isNettyOnClasspath();

  private NettyBufferSupport() {
    // Utility class
  }

  private static boolean isNettyOnClasspath() {
    try {
      Class.forName("io.netty.buffer.ByteBuf");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /**
   * Checks if Netty buffer support is available.
   *
   * <p>Call this method before using any Netty-specific methods to ensure
   * graceful degradation when Netty is not on the classpath.
   *
   * @return true if Netty is available on the classpath
   */
  public static boolean isAvailable() {
    return NETTY_AVAILABLE;
  }

  /**
   * Creates a Multi that emits Netty ByteBuf chunks instead of byte arrays.
   *
   * <p><strong>Important:</strong> ByteBufs MUST be released by the consumer
   * by calling {@code ByteBuf.release()} after processing each buffer.
   *
   * @param source the source Multi emitting byte arrays
   * @return Multi emitting ByteBuf instances wrapping the byte arrays
   * @throws IllegalStateException if Netty is not available
   */
  public static Multi<io.netty.buffer.ByteBuf> toNettyBuffers(Multi<byte[]> source) {
    checkNettyAvailable();
    return source.onItem().transform(bytes ->
        io.netty.buffer.Unpooled.wrappedBuffer(bytes));
  }

  /**
   * Creates a Multi that emits Netty ByteBuf chunks using pooled allocation.
   *
   * <p>Pooled buffers provide better performance for high-throughput scenarios
   * by reusing buffer memory from a pool instead of allocating new arrays.
   *
   * <p><strong>Important:</strong> ByteBufs MUST be released by the consumer
   * by calling {@code ByteBuf.release()} after processing each buffer.
   *
   * @param source the source Multi emitting byte arrays
   * @return Multi emitting ByteBuf instances using pooled allocation
   * @throws IllegalStateException if Netty is not available
   */
  public static Multi<io.netty.buffer.ByteBuf> toPooledNettyBuffers(Multi<byte[]> source) {
    checkNettyAvailable();
    return source.onItem().transform(bytes -> {
      io.netty.buffer.ByteBuf buf = io.netty.buffer.PooledByteBufAllocator.DEFAULT.buffer(bytes.length);
      buf.writeBytes(bytes);
      return buf;
    });
  }

  /**
   * Converts a ByteBuf Multi back to byte array Multi with proper release.
   *
   * <p>This method handles the release of ByteBufs automatically, so consumers
   * don't need to manage buffer lifecycle.
   *
   * @param source the source Multi emitting ByteBufs
   * @return Multi emitting byte arrays
   * @throws IllegalStateException if Netty is not available
   */
  public static Multi<byte[]> fromNettyBuffers(Multi<io.netty.buffer.ByteBuf> source) {
    checkNettyAvailable();
    return source.onItem().transform(buf -> {
      try {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
      } finally {
        buf.release();
      }
    });
  }

  /**
   * Wraps a byte array as a Netty ByteBuf without copying.
   *
   * <p><strong>Important:</strong> The returned ByteBuf shares the byte array's memory.
   * Modifying the byte array will affect the ByteBuf and vice versa.
   *
   * @param bytes the byte array to wrap
   * @return a ByteBuf wrapping the byte array
   * @throws IllegalStateException if Netty is not available
   */
  public static io.netty.buffer.ByteBuf wrap(byte[] bytes) {
    checkNettyAvailable();
    return io.netty.buffer.Unpooled.wrappedBuffer(bytes);
  }

  /**
   * Allocates a pooled ByteBuf with the specified capacity.
   *
   * @param capacity the initial capacity
   * @return a pooled ByteBuf
   * @throws IllegalStateException if Netty is not available
   */
  public static io.netty.buffer.ByteBuf allocate(int capacity) {
    checkNettyAvailable();
    return io.netty.buffer.PooledByteBufAllocator.DEFAULT.buffer(capacity);
  }

  private static void checkNettyAvailable() {
    if (!NETTY_AVAILABLE) {
      throw new IllegalStateException(
          "Netty ByteBuf support requires netty-buffer dependency. " +
          "Add io.netty:netty-buffer to your project.");
    }
  }
}
