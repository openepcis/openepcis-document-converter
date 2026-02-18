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

import io.openepcis.reactive.util.NettyBufferSupport;
import io.smallrye.mutiny.Multi;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Abstraction for reactive input sources in EPCIS document conversion pipelines.
 *
 * <p>This class provides factory methods to create reactive sources from various input types:
 * <ul>
 *   <li>{@link Flow.Publisher}&lt;{@link ByteBuffer}&gt; - Primary reactive input for HTTP streaming, messaging</li>
 *   <li>{@link Multi}&lt;{@link ByteBuffer}&gt; - Mutiny Multi input</li>
 *   <li>{@link Flow.Publisher}&lt;{@code io.netty.buffer.ByteBuf}&gt; - Netty reactive input</li>
 *   <li>{@link Multi}&lt;{@code io.netty.buffer.ByteBuf}&gt; - Mutiny Netty input</li>
 *   <li>{@link InputStream} - Backward compatibility, wrapped as reactive source</li>
 *   <li>byte[] - Convenience method for in-memory data</li>
 * </ul>
 *
 * <p><strong>Backpressure:</strong> All sources respect downstream demand. Data is only read
 * from the underlying source when subscribers request it via {@link Flow.Subscription#request(long)}.
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * // From reactive publisher (e.g., HTTP body)
 * Flow.Publisher<ByteBuffer> httpBody = ...;
 * ReactiveConversionSource source = ReactiveConversionSource.fromPublisher(httpBody);
 *
 * // From Mutiny Multi
 * Multi<ByteBuffer> multi = ...;
 * ReactiveConversionSource source = ReactiveConversionSource.fromMulti(multi);
 *
 * // From Netty ByteBuf
 * Flow.Publisher<ByteBuf> nettyPublisher = ...;
 * ReactiveConversionSource source = ReactiveConversionSource.fromNettyPublisher(nettyPublisher);
 *
 * // From InputStream (backward compatibility)
 * try (InputStream is = new FileInputStream("document.xml")) {
 *     ReactiveConversionSource source = ReactiveConversionSource.fromInputStream(is);
 *     // Use source...
 * }
 *
 * // From byte array (testing/convenience)
 * byte[] data = Files.readAllBytes(path);
 * ReactiveConversionSource source = ReactiveConversionSource.fromBytes(data);
 * }</pre>
 *
 * @see ReactiveVersionTransformer
 */
public final class ReactiveConversionSource {

  private static final Logger LOG = System.getLogger(ReactiveConversionSource.class.getName());

  /** Default buffer size for InputStream reading (8KB as per requirements) */
  public static final int DEFAULT_BUFFER_SIZE = 8192;

  private final Flow.Publisher<ByteBuffer> publisher;
  private final Callable<Flow.Publisher<ByteBuffer>> retrySource;
  private final boolean isNettySource;
  private final Object originalNettyPublisher; // Store original for toNettyMulti()

  private ReactiveConversionSource(
      Flow.Publisher<ByteBuffer> publisher,
      Callable<Flow.Publisher<ByteBuffer>> retrySource,
      boolean isNettySource,
      Object originalNettyPublisher) {
    this.publisher = Objects.requireNonNull(publisher, "Publisher cannot be null");
    this.retrySource = retrySource;
    this.isNettySource = isNettySource;
    this.originalNettyPublisher = originalNettyPublisher;
  }

  // ==================== Factory Methods: Flow.Publisher<ByteBuffer> ====================

  /**
   * Creates a source from a reactive ByteBuffer publisher.
   *
   * <p>This is the primary input method for truly non-blocking I/O from HTTP streams,
   * message queues, or other reactive sources.
   *
   * @param publisher the reactive ByteBuffer source
   * @return new ReactiveConversionSource
   * @throws NullPointerException if publisher is null
   */
  public static ReactiveConversionSource fromPublisher(Flow.Publisher<ByteBuffer> publisher) {
    return new ReactiveConversionSource(publisher, null, false, null);
  }

  /**
   * Creates a source from a reactive ByteBuffer publisher with retry support.
   *
   * <p>The retry source is used for early-eventList handling in EPCIS documents,
   * where the eventList appears before the @context field.
   *
   * @param publisher the reactive ByteBuffer source
   * @param retrySource callable to get a fresh source for retry
   * @return new ReactiveConversionSource
   * @throws NullPointerException if publisher is null
   */
  public static ReactiveConversionSource fromPublisher(
      Flow.Publisher<ByteBuffer> publisher,
      Callable<Flow.Publisher<ByteBuffer>> retrySource) {
    return new ReactiveConversionSource(publisher, retrySource, false, null);
  }

  // ==================== Factory Methods: Multi<ByteBuffer> ====================

  /**
   * Creates a source from a Mutiny Multi of ByteBuffers.
   *
   * <p>Convenience method for Mutiny-based applications.
   *
   * @param multi the Mutiny Multi source
   * @return new ReactiveConversionSource
   * @throws NullPointerException if multi is null
   */
  public static ReactiveConversionSource fromMulti(Multi<ByteBuffer> multi) {
    Objects.requireNonNull(multi, "Multi cannot be null");
    return new ReactiveConversionSource(multi, null, false, null);
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
    Objects.requireNonNull(multi, "Multi cannot be null");
    Callable<Flow.Publisher<ByteBuffer>> wrappedRetry = retrySource == null ? null
        : () -> retrySource.call();
    return new ReactiveConversionSource(multi, wrappedRetry, false, null);
  }

  // ==================== Factory Methods: Netty ByteBuf ====================

  /**
   * Creates a source from a Netty ByteBuf publisher.
   *
   * <p>ByteBufs are automatically converted to ByteBuffers. The original ByteBufs
   * are released after conversion.
   *
   * @param publisher the Netty ByteBuf publisher
   * @return new ReactiveConversionSource
   * @throws NullPointerException if publisher is null
   * @throws IllegalStateException if Netty is not available
   */
  public static ReactiveConversionSource fromNettyPublisher(
      Flow.Publisher<io.netty.buffer.ByteBuf> publisher) {
    Objects.requireNonNull(publisher, "Publisher cannot be null");
    NettyBufferSupport.checkAvailable();

    // Convert ByteBuf publisher to ByteBuffer publisher
    Multi<ByteBuffer> converted = Multi.createFrom().publisher(publisher)
        .onItem().transform(buf -> {
          try {
            ByteBuffer nioBuffer = buf.nioBuffer();
            // Make a copy since ByteBuf may be reused
            ByteBuffer copy = ByteBuffer.allocate(nioBuffer.remaining());
            copy.put(nioBuffer);
            copy.flip();
            return copy;
          } finally {
            buf.release();
          }
        });

    return new ReactiveConversionSource(converted, null, true, publisher);
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
    Objects.requireNonNull(multi, "Multi cannot be null");
    NettyBufferSupport.checkAvailable();

    // Convert ByteBuf Multi to ByteBuffer Multi
    Multi<ByteBuffer> converted = multi
        .onItem().transform(buf -> {
          try {
            ByteBuffer nioBuffer = buf.nioBuffer();
            ByteBuffer copy = ByteBuffer.allocate(nioBuffer.remaining());
            copy.put(nioBuffer);
            copy.flip();
            return copy;
          } finally {
            buf.release();
          }
        });

    return new ReactiveConversionSource(converted, null, true, multi);
  }

  // ==================== Factory Methods: InputStream ====================

  /**
   * Creates a source from an InputStream.
   *
   * <p>The InputStream is read in chunks on-demand as subscribers request items.
   * Uses default buffer size of 8KB.
   *
   * <p><strong>Resource lifecycle:</strong> The InputStream will be automatically closed when:
   * <ul>
   *   <li>The stream is fully consumed (normal completion)</li>
   *   <li>An error occurs during reading</li>
   *   <li>The subscription is cancelled by the downstream subscriber</li>
   * </ul>
   * Callers should still wrap in try-with-resources for exception safety during setup.
   *
   * @param inputStream the InputStream to read from
   * @return new ReactiveConversionSource
   * @throws NullPointerException if inputStream is null
   */
  public static ReactiveConversionSource fromInputStream(InputStream inputStream) {
    return fromInputStream(inputStream, DEFAULT_BUFFER_SIZE);
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
    Objects.requireNonNull(inputStream, "InputStream cannot be null");
    if (bufferSize <= 0) {
      throw new IllegalArgumentException("Buffer size must be positive: " + bufferSize);
    }
    return new ReactiveConversionSource(
        createInputStreamPublisher(inputStream, bufferSize),
        null, false, null);
  }

  /**
   * Creates a source from an InputStream with retry support.
   *
   * <p>The retry callable is used for early-eventList handling.
   *
   * @param inputStream the primary InputStream
   * @param retryInputStream callable to get a fresh InputStream for retry
   * @return new ReactiveConversionSource
   * @throws NullPointerException if inputStream is null
   */
  public static ReactiveConversionSource fromInputStream(
      InputStream inputStream,
      Callable<InputStream> retryInputStream) {
    return fromInputStream(inputStream, retryInputStream, DEFAULT_BUFFER_SIZE);
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
    Objects.requireNonNull(inputStream, "InputStream cannot be null");
    if (bufferSize <= 0) {
      throw new IllegalArgumentException("Buffer size must be positive: " + bufferSize);
    }

    Callable<Flow.Publisher<ByteBuffer>> retryPublisher = retryInputStream == null ? null : () -> {
      InputStream retryStream = retryInputStream.call();
      return createInputStreamPublisher(retryStream, bufferSize);
    };

    return new ReactiveConversionSource(
        createInputStreamPublisher(inputStream, bufferSize),
        retryPublisher, false, null);
  }

  // ==================== Factory Methods: byte[] ====================

  /**
   * Creates a source from a byte array.
   *
   * <p>Useful for testing or parsing in-memory data.
   *
   * @param bytes the byte array containing document data
   * @return new ReactiveConversionSource
   * @throws NullPointerException if bytes is null
   */
  public static ReactiveConversionSource fromBytes(byte[] bytes) {
    Objects.requireNonNull(bytes, "Bytes array cannot be null");
    return new ReactiveConversionSource(createBytesPublisher(bytes), null, false, null);
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
    Objects.requireNonNull(bytes, "Bytes array cannot be null");
    Callable<Flow.Publisher<ByteBuffer>> retryPublisher = retryBytes == null ? null
        : () -> createBytesPublisher(retryBytes.call());
    return new ReactiveConversionSource(createBytesPublisher(bytes), retryPublisher, false, null);
  }

  // ==================== Accessors ====================

  /**
   * Returns the underlying Flow.Publisher.
   *
   * @return the publisher
   */
  public Flow.Publisher<ByteBuffer> toPublisher() {
    return publisher;
  }

  /**
   * Converts to a Mutiny Multi for easier reactive composition.
   *
   * @return Multi wrapping this source
   */
  public Multi<ByteBuffer> toMulti() {
    return Multi.createFrom().publisher(publisher);
  }

  /**
   * Returns the retry source callable, if configured.
   *
   * @return retry source callable, or null if not configured
   */
  public Callable<Flow.Publisher<ByteBuffer>> retrySource() {
    return retrySource;
  }

  /**
   * Checks if this source has retry support configured.
   *
   * @return true if retry source is available
   */
  public boolean hasRetrySupport() {
    return retrySource != null;
  }

  /**
   * Checks if this source was created from a Netty ByteBuf publisher.
   *
   * @return true if this is a Netty source
   */
  public boolean isNettySource() {
    return isNettySource;
  }

  /**
   * Returns the original Netty ByteBuf Multi if this source was created from Netty.
   *
   * <p><strong>Warning:</strong> Only call this if {@link #isNettySource()} returns true.
   * The returned Multi may not be reusable after the first subscription.
   *
   * @return the original Netty Multi, or throws if not a Netty source
   * @throws IllegalStateException if this is not a Netty source
   */
  @SuppressWarnings("unchecked")
  public Multi<io.netty.buffer.ByteBuf> toNettyMulti() {
    if (!isNettySource || originalNettyPublisher == null) {
      throw new IllegalStateException("This source was not created from a Netty publisher");
    }
    if (originalNettyPublisher instanceof Multi) {
      return (Multi<io.netty.buffer.ByteBuf>) originalNettyPublisher;
    }
    return Multi.createFrom().publisher((Flow.Publisher<io.netty.buffer.ByteBuf>) originalNettyPublisher);
  }

  // ==================== Internal Publisher Factories ====================

  /**
   * Creates a Flow.Publisher from an InputStream with backpressure support.
   */
  private static Flow.Publisher<ByteBuffer> createInputStreamPublisher(
      InputStream inputStream, int bufferSize) {
    return subscriber -> {
      subscriber.onSubscribe(new Flow.Subscription() {
        private volatile boolean cancelled = false;
        private volatile boolean done = false;
        private final byte[] buffer = new byte[bufferSize];
        private final AtomicLong requested = new AtomicLong();
        private final AtomicInteger wip = new AtomicInteger();

        @Override
        public void request(long n) {
          if (n <= 0 || cancelled) return;
          // Accumulate demand atomically — safe under re-entrant request()
          long current;
          do {
            current = requested.get();
            if (current == Long.MAX_VALUE) return;
          } while (!requested.compareAndSet(current,
              (Long.MAX_VALUE - current < n) ? Long.MAX_VALUE : current + n));
          drain();
        }

        private void drain() {
          // wip gate: only one thread (or one stack frame) drains at a time.
          // Re-entrant request() from onNext() increments wip but returns here.
          if (wip.getAndIncrement() != 0) return;

          int missed = 1;
          try {
            for (;;) {
              long emitted = 0;
              long demand = requested.get();

              while (emitted < demand && !cancelled && !done) {
                int bytesRead = inputStream.read(buffer);
                if (bytesRead == -1) {
                  done = true;
                  closeQuietly();
                  subscriber.onComplete();
                  return;
                }
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                subscriber.onNext(ByteBuffer.wrap(chunk));
                emitted++;
              }

              if (cancelled || done) return;

              if (emitted > 0) {
                requested.addAndGet(-emitted);
              }

              // Check if more work arrived while we were draining
              missed = wip.addAndGet(-missed);
              if (missed == 0) return;
            }
          } catch (IOException e) {
            if (!cancelled && !done) {
              done = true;
              closeQuietly();
              subscriber.onError(e);
            }
          }
        }

        private void closeQuietly() {
          try {
            inputStream.close();
          } catch (IOException e) {
            LOG.log(Level.DEBUG, "Error closing InputStream", e);
          }
        }

        @Override
        public void cancel() {
          cancelled = true;
          closeQuietly();
        }
      });
    };
  }

  /**
   * Creates a Flow.Publisher from a byte array.
   */
  private static Flow.Publisher<ByteBuffer> createBytesPublisher(byte[] bytes) {
    return subscriber -> {
      subscriber.onSubscribe(new Flow.Subscription() {
        private boolean completed = false;

        @Override
        public void request(long n) {
          if (!completed && n > 0) {
            completed = true;
            subscriber.onNext(ByteBuffer.wrap(bytes));
            subscriber.onComplete();
          }
        }

        @Override
        public void cancel() {
          completed = true;
        }
      });
    };
  }
}
