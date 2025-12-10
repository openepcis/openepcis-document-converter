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
import java.util.concurrent.Flow;

/**
 * Optional Netty ByteBuf support for zero-copy buffer operations.
 *
 * <p>This class delegates to {@link io.openepcis.reactive.util.NettyBufferSupport} in the
 * openepcis-reactive-event-publisher module and provides backward compatibility for
 * existing code using this class.
 *
 * <p>For new code, consider using {@link io.openepcis.reactive.util.NettyBufferSupport} directly.
 *
 * @see io.openepcis.reactive.util.NettyBufferSupport
 * @see ReactiveVersionTransformer
 * @deprecated Use {@link io.openepcis.reactive.util.NettyBufferSupport} instead
 */
@Deprecated(since = "999-SNAPSHOT", forRemoval = false)
public final class NettyBufferSupport {

  private NettyBufferSupport() {
    // Utility class
  }

  /**
   * Checks if Netty buffer support is available.
   *
   * @return true if Netty is available on the classpath
   */
  public static boolean isAvailable() {
    return io.openepcis.reactive.util.NettyBufferSupport.isAvailable();
  }

  // ==================== Multi Conversion: byte[] ↔ ByteBuf ====================

  /**
   * Creates a Multi that emits Netty ByteBuf chunks instead of byte arrays.
   *
   * @param source the source Multi emitting byte arrays
   * @return Multi emitting ByteBuf instances wrapping the byte arrays
   * @throws IllegalStateException if Netty is not available
   */
  public static Multi<io.netty.buffer.ByteBuf> toNettyBuffers(Multi<byte[]> source) {
    return io.openepcis.reactive.util.NettyBufferSupport.toNettyBuffers(source);
  }

  /**
   * Creates a Multi that emits Netty ByteBuf chunks using pooled allocation.
   *
   * @param source the source Multi emitting byte arrays
   * @return Multi emitting ByteBuf instances using pooled allocation
   * @throws IllegalStateException if Netty is not available
   */
  public static Multi<io.netty.buffer.ByteBuf> toPooledNettyBuffers(Multi<byte[]> source) {
    return io.openepcis.reactive.util.NettyBufferSupport.toPooledNettyBuffers(source);
  }

  /**
   * Converts a ByteBuf Multi back to byte array Multi with proper release.
   *
   * @param source the source Multi emitting ByteBufs
   * @return Multi emitting byte arrays
   * @throws IllegalStateException if Netty is not available
   */
  public static Multi<byte[]> fromNettyBuffers(Multi<io.netty.buffer.ByteBuf> source) {
    return io.openepcis.reactive.util.NettyBufferSupport.fromNettyBuffers(source);
  }

  // ==================== Multi Conversion: ByteBuffer ↔ ByteBuf ====================

  /**
   * Converts a ByteBuf Multi to a ByteBuffer Multi with proper release.
   *
   * @param source the source Multi emitting ByteBufs
   * @return Multi emitting ByteBuffers
   * @throws IllegalStateException if Netty is not available
   */
  public static Multi<ByteBuffer> toByteBuffers(Multi<io.netty.buffer.ByteBuf> source) {
    return io.openepcis.reactive.util.NettyBufferSupport.toByteBuffers(source);
  }

  /**
   * Converts a ByteBuffer Multi to a ByteBuf Multi.
   *
   * @param source the source Multi emitting ByteBuffers
   * @return Multi emitting ByteBufs wrapping the ByteBuffer data
   * @throws IllegalStateException if Netty is not available
   */
  public static Multi<io.netty.buffer.ByteBuf> fromByteBuffers(Multi<ByteBuffer> source) {
    return io.openepcis.reactive.util.NettyBufferSupport.fromByteBuffers(source);
  }

  /**
   * Converts a ByteBuffer Multi to a pooled ByteBuf Multi.
   *
   * @param source the source Multi emitting ByteBuffers
   * @return Multi emitting pooled ByteBufs
   * @throws IllegalStateException if Netty is not available
   */
  public static Multi<io.netty.buffer.ByteBuf> fromByteBuffersPooled(Multi<ByteBuffer> source) {
    return io.openepcis.reactive.util.NettyBufferSupport.fromByteBuffersPooled(source);
  }

  // ==================== Flow.Publisher Adaptation ====================

  /**
   * Adapts a Flow.Publisher of ByteBuf to a Flow.Publisher of ByteBuffer.
   *
   * @param source the ByteBuf publisher
   * @return adapted ByteBuffer publisher
   * @throws IllegalArgumentException if source is null
   * @throws IllegalStateException if Netty is not available
   */
  public static Flow.Publisher<ByteBuffer> adaptToByteBuffer(
      Flow.Publisher<io.netty.buffer.ByteBuf> source) {
    return io.openepcis.reactive.util.NettyBufferSupport.adaptToByteBuffer(source);
  }

  // ==================== Direct Buffer Operations ====================

  /**
   * Wraps a byte array as a Netty ByteBuf without copying.
   *
   * @param bytes the byte array to wrap
   * @return a ByteBuf wrapping the byte array
   * @throws IllegalStateException if Netty is not available
   */
  public static io.netty.buffer.ByteBuf wrap(byte[] bytes) {
    return io.openepcis.reactive.util.NettyBufferSupport.wrap(bytes);
  }

  /**
   * Wraps a ByteBuffer as a Netty ByteBuf without copying.
   *
   * @param buffer the ByteBuffer to wrap
   * @return a ByteBuf wrapping the ByteBuffer
   * @throws IllegalStateException if Netty is not available
   */
  public static io.netty.buffer.ByteBuf wrap(ByteBuffer buffer) {
    return io.openepcis.reactive.util.NettyBufferSupport.wrap(buffer);
  }

  /**
   * Allocates a pooled ByteBuf with the specified capacity.
   *
   * @param capacity the initial capacity
   * @return a pooled ByteBuf
   * @throws IllegalStateException if Netty is not available
   */
  public static io.netty.buffer.ByteBuf allocate(int capacity) {
    return io.openepcis.reactive.util.NettyBufferSupport.allocate(capacity);
  }

  /**
   * Allocates an unpooled ByteBuf with the specified capacity.
   *
   * @param capacity the initial capacity
   * @return an unpooled ByteBuf
   * @throws IllegalStateException if Netty is not available
   */
  public static io.netty.buffer.ByteBuf allocateUnpooled(int capacity) {
    return io.openepcis.reactive.util.NettyBufferSupport.allocateUnpooled(capacity);
  }

  /**
   * Allocates a direct (off-heap) pooled ByteBuf with the specified capacity.
   *
   * @param capacity the initial capacity
   * @return a direct pooled ByteBuf
   * @throws IllegalStateException if Netty is not available
   */
  public static io.netty.buffer.ByteBuf allocateDirect(int capacity) {
    return io.openepcis.reactive.util.NettyBufferSupport.allocateDirect(capacity);
  }

  // ==================== Availability Check ====================

  /**
   * Checks if Netty is available and throws if not.
   *
   * <p>This method is package-private for use by other reactive classes.
   *
   * @throws IllegalStateException if Netty is not available
   */
  static void checkNettyAvailable() {
    io.openepcis.reactive.util.NettyBufferSupport.checkAvailable();
  }
}
