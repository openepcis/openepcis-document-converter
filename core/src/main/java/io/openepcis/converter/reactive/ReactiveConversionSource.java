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
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Flow;

/**
 * Abstraction for reactive input sources in the document conversion pipeline.
 *
 * <p>This class provides factory methods to create reactive sources from various input types:
 * <ul>
 *   <li>{@link Flow.Publisher}&lt;{@link ByteBuffer}&gt; - Primary reactive input for HTTP streaming, messaging</li>
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

  private ReactiveConversionSource(
      Flow.Publisher<ByteBuffer> publisher,
      Callable<Flow.Publisher<ByteBuffer>> retrySource) {
    this.publisher = Objects.requireNonNull(publisher, "Publisher cannot be null");
    this.retrySource = retrySource;
  }

  // ==================== Factory Methods ====================

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
    return new ReactiveConversionSource(publisher, null);
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
    return new ReactiveConversionSource(publisher, retrySource);
  }

  /**
   * Creates a source from a Mutiny Multi.
   *
   * <p>Convenience method for Mutiny-based applications.
   *
   * @param multi the Mutiny Multi source
   * @return new ReactiveConversionSource
   * @throws NullPointerException if multi is null
   */
  public static ReactiveConversionSource fromMulti(Multi<ByteBuffer> multi) {
    Objects.requireNonNull(multi, "Multi cannot be null");
    return new ReactiveConversionSource(multi, null);
  }

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
        null);
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
        retryPublisher);
  }

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
    return new ReactiveConversionSource(createBytesPublisher(bytes), null);
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
    return new ReactiveConversionSource(createBytesPublisher(bytes), retryPublisher);
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

  // ==================== Internal Publisher Factories ====================

  /**
   * Creates a Flow.Publisher from an InputStream with backpressure support.
   */
  private static Flow.Publisher<ByteBuffer> createInputStreamPublisher(
      InputStream inputStream, int bufferSize) {
    return subscriber -> {
      subscriber.onSubscribe(new Flow.Subscription() {
        private volatile boolean cancelled = false;
        private final byte[] buffer = new byte[bufferSize];

        @Override
        public void request(long n) {
          if (cancelled) return;

          try {
            for (long i = 0; i < n && !cancelled; i++) {
              int bytesRead = inputStream.read(buffer);
              if (bytesRead == -1) {
                if (!cancelled) {
                  closeQuietly();
                  subscriber.onComplete();
                }
                return;
              }
              // Create a copy for the ByteBuffer
              byte[] chunk = new byte[bytesRead];
              System.arraycopy(buffer, 0, chunk, 0, bytesRead);
              subscriber.onNext(ByteBuffer.wrap(chunk));
            }
          } catch (IOException e) {
            if (!cancelled) {
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
          // Close the underlying stream on cancellation to prevent resource leaks
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
