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

import io.openepcis.reactive.util.ReactiveSource;
import io.smallrye.mutiny.Multi;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Callable;
import java.util.concurrent.Flow;

/**
 * Abstraction for reactive input sources in the document conversion pipeline.
 *
 * <p>This class delegates to {@link io.openepcis.reactive.util.ReactiveSource} in the
 * openepcis-reactive-event-publisher module and provides backward compatibility for
 * existing code using this class.
 *
 * <p>For new code, consider using {@link io.openepcis.reactive.util.ReactiveSource} directly.
 *
 * @see io.openepcis.reactive.util.ReactiveSource
 * @see ReactiveVersionTransformer
 * @deprecated Use {@link io.openepcis.reactive.util.ReactiveSource} instead
 */
@Deprecated(since = "999-SNAPSHOT", forRemoval = false)
public final class ReactiveConversionSource {

  /** Default buffer size for InputStream reading (8KB as per requirements) */
  public static final int DEFAULT_BUFFER_SIZE = ReactiveSource.DEFAULT_BUFFER_SIZE;

  private final ReactiveSource delegate;

  private ReactiveConversionSource(ReactiveSource delegate) {
    this.delegate = delegate;
  }

  // ==================== Factory Methods: Flow.Publisher<ByteBuffer> ====================

  /**
   * Creates a source from a reactive ByteBuffer publisher.
   *
   * @param publisher the reactive ByteBuffer source
   * @return new ReactiveConversionSource
   * @throws NullPointerException if publisher is null
   */
  public static ReactiveConversionSource fromPublisher(Flow.Publisher<ByteBuffer> publisher) {
    return new ReactiveConversionSource(ReactiveSource.fromPublisher(publisher));
  }

  /**
   * Creates a source from a reactive ByteBuffer publisher with retry support.
   *
   * @param publisher the reactive ByteBuffer source
   * @param retrySource callable to get a fresh source for retry
   * @return new ReactiveConversionSource
   * @throws NullPointerException if publisher is null
   */
  public static ReactiveConversionSource fromPublisher(
      Flow.Publisher<ByteBuffer> publisher,
      Callable<Flow.Publisher<ByteBuffer>> retrySource) {
    return new ReactiveConversionSource(ReactiveSource.fromPublisher(publisher, retrySource));
  }

  // ==================== Factory Methods: Multi<ByteBuffer> ====================

  /**
   * Creates a source from a Mutiny Multi of ByteBuffers.
   *
   * @param multi the Mutiny Multi source
   * @return new ReactiveConversionSource
   * @throws NullPointerException if multi is null
   */
  public static ReactiveConversionSource fromMulti(Multi<ByteBuffer> multi) {
    return new ReactiveConversionSource(ReactiveSource.fromMulti(multi));
  }

  /**
   * Creates a source from a Mutiny Multi with retry support.
   *
   * @param multi the Mutiny Multi source
   * @param retrySource callable to get a fresh Multi for retry
   * @return new ReactiveConversionSource
   * @throws NullPointerException if multi is null
   */
  public static ReactiveConversionSource fromMulti(
      Multi<ByteBuffer> multi,
      Callable<Multi<ByteBuffer>> retrySource) {
    return new ReactiveConversionSource(ReactiveSource.fromMulti(multi, retrySource));
  }

  // ==================== Factory Methods: Netty ByteBuf ====================

  /**
   * Creates a source from a Netty ByteBuf publisher.
   *
   * @param publisher the Netty ByteBuf publisher
   * @return new ReactiveConversionSource
   * @throws NullPointerException if publisher is null
   * @throws IllegalStateException if Netty is not available
   */
  public static ReactiveConversionSource fromNettyPublisher(
      Flow.Publisher<io.netty.buffer.ByteBuf> publisher) {
    return new ReactiveConversionSource(ReactiveSource.fromNettyPublisher(publisher));
  }

  /**
   * Creates a source from a Mutiny Multi of Netty ByteBufs.
   *
   * @param multi the Mutiny Multi of ByteBufs
   * @return new ReactiveConversionSource
   * @throws NullPointerException if multi is null
   * @throws IllegalStateException if Netty is not available
   */
  public static ReactiveConversionSource fromNettyMulti(Multi<io.netty.buffer.ByteBuf> multi) {
    return new ReactiveConversionSource(ReactiveSource.fromNettyMulti(multi));
  }

  // ==================== Factory Methods: InputStream ====================

  /**
   * Creates a source from an InputStream.
   *
   * @param inputStream the InputStream to read from
   * @return new ReactiveConversionSource
   * @throws NullPointerException if inputStream is null
   */
  public static ReactiveConversionSource fromInputStream(InputStream inputStream) {
    return new ReactiveConversionSource(ReactiveSource.fromInputStream(inputStream));
  }

  /**
   * Creates a source from an InputStream with custom buffer size.
   *
   * @param inputStream the InputStream to read from
   * @param bufferSize the size of read buffer in bytes
   * @return new ReactiveConversionSource
   * @throws NullPointerException if inputStream is null
   * @throws IllegalArgumentException if bufferSize is not positive
   */
  public static ReactiveConversionSource fromInputStream(InputStream inputStream, int bufferSize) {
    return new ReactiveConversionSource(ReactiveSource.fromInputStream(inputStream, bufferSize));
  }

  /**
   * Creates a source from an InputStream with retry support.
   *
   * @param inputStream the primary InputStream
   * @param retryInputStream callable to get a fresh InputStream for retry
   * @return new ReactiveConversionSource
   * @throws NullPointerException if inputStream is null
   */
  public static ReactiveConversionSource fromInputStream(
      InputStream inputStream,
      Callable<InputStream> retryInputStream) {
    return new ReactiveConversionSource(
        ReactiveSource.fromInputStream(inputStream, retryInputStream));
  }

  /**
   * Creates a source from an InputStream with retry support and custom buffer size.
   *
   * @param inputStream the primary InputStream
   * @param retryInputStream callable to get a fresh InputStream for retry
   * @param bufferSize the size of read buffer in bytes
   * @return new ReactiveConversionSource
   * @throws NullPointerException if inputStream is null
   * @throws IllegalArgumentException if bufferSize is not positive
   */
  public static ReactiveConversionSource fromInputStream(
      InputStream inputStream,
      Callable<InputStream> retryInputStream,
      int bufferSize) {
    return new ReactiveConversionSource(
        ReactiveSource.fromInputStream(inputStream, retryInputStream, bufferSize));
  }

  // ==================== Factory Methods: byte[] ====================

  /**
   * Creates a source from a byte array.
   *
   * @param bytes the byte array containing document data
   * @return new ReactiveConversionSource
   * @throws NullPointerException if bytes is null
   */
  public static ReactiveConversionSource fromBytes(byte[] bytes) {
    return new ReactiveConversionSource(ReactiveSource.fromBytes(bytes));
  }

  /**
   * Creates a source from a byte array with retry support.
   *
   * @param bytes the byte array containing document data
   * @param retryBytes callable to get a fresh byte array for retry
   * @return new ReactiveConversionSource
   * @throws NullPointerException if bytes is null
   */
  public static ReactiveConversionSource fromBytes(byte[] bytes, Callable<byte[]> retryBytes) {
    return new ReactiveConversionSource(ReactiveSource.fromBytes(bytes, retryBytes));
  }

  // ==================== Accessors ====================

  /**
   * Returns the underlying Flow.Publisher.
   *
   * @return the publisher
   */
  public Flow.Publisher<ByteBuffer> toPublisher() {
    return delegate.toPublisher();
  }

  /**
   * Converts to a Mutiny Multi for easier reactive composition.
   *
   * @return Multi wrapping this source
   */
  public Multi<ByteBuffer> toMulti() {
    return delegate.toMulti();
  }

  /**
   * Returns the retry source callable, if configured.
   *
   * @return retry source callable, or null if not configured
   */
  public Callable<Flow.Publisher<ByteBuffer>> retrySource() {
    return delegate.retrySource();
  }

  /**
   * Checks if this source has retry support configured.
   *
   * @return true if retry source is available
   */
  public boolean hasRetrySupport() {
    return delegate.hasRetrySupport();
  }

  /**
   * Checks if this source was created from a Netty ByteBuf publisher.
   *
   * @return true if this is a Netty source
   */
  public boolean isNettySource() {
    return delegate.isNettySource();
  }

  /**
   * Returns the original Netty ByteBuf Multi if this source was created from Netty.
   *
   * @return the original Netty Multi, or throws if not a Netty source
   * @throws IllegalStateException if this is not a Netty source
   */
  public Multi<io.netty.buffer.ByteBuf> toNettyMulti() {
    return delegate.toNettyMulti();
  }
}
