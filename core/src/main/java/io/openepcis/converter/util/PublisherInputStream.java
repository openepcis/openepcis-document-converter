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

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Backpressure-aware bridge from {@link Flow.Publisher}&lt;ByteBuffer&gt; to {@link InputStream}.
 *
 * <p>Converts reactive {@code Flow.Publisher<ByteBuffer>} streams to traditional
 * {@code InputStream} for use with synchronous APIs, while maintaining backpressure control.
 *
 * <p>Since Mutiny's {@code Multi<T>} implements {@code Flow.Publisher<T>}, a
 * {@code Multi<ByteBuffer>} can be passed directly to {@link #from(Flow.Publisher)}.
 */
public final class PublisherInputStream extends InputStream
    implements Flow.Subscriber<ByteBuffer>, AutoCloseable {

  private static final Logger LOG = System.getLogger(PublisherInputStream.class.getName());
  private static final Object END = new Object();

  /**
   * Prefetch count - how many chunks to request ahead. This allows the publisher to send data
   * without waiting for consumer. Using unbounded queue since we control demand via prefetch.
   */
  private static final int PREFETCH = 16;

  /**
   * Timeout for waiting on data from publisher (in minutes). Prevents indefinite hangs if the
   * publisher stops producing data.
   */
  private static final int QUEUE_POLL_TIMEOUT_MINUTES = 5;

  // Unbounded queue - backpressure is controlled via subscription.request(n)
  private final LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>();
  private volatile Flow.Subscription subscription;
  private volatile boolean closed;

  /**
   * Tracks outstanding requests to the publisher. We request more when this drops below PREFETCH/2.
   */
  private final AtomicInteger pendingRequests = new AtomicInteger(0);

  private byte[] current;
  private int idx;
  private long totalBytesReceived = 0;
  private long chunksReceived = 0;

  /**
   * Creates a PublisherInputStream from a {@link Flow.Publisher}&lt;ByteBuffer&gt;.
   *
   * <p>This is a convenience factory method that encapsulates the create-and-subscribe pattern.
   * Since Mutiny's {@code Multi<ByteBuffer>} implements {@code Flow.Publisher<ByteBuffer>}, it can
   * be passed directly:
   *
   * <pre>{@code
   * Multi<ByteBuffer> multi = reactiveTransformer.convertToByteBuffers(input, conversion);
   * InputStream stream = PublisherInputStream.from(multi);
   * }</pre>
   *
   * @param publisher The source Publisher to convert
   * @return A subscribed InputStream ready for reading
   */
  public static PublisherInputStream from(Flow.Publisher<ByteBuffer> publisher) {
    LOG.log(
        Level.TRACE,
        () -> "Creating PublisherInputStream from Publisher: " + publisher.getClass().getName());
    PublisherInputStream stream = new PublisherInputStream();
    publisher.subscribe(stream);
    LOG.log(Level.TRACE, "Subscribed to publisher, returning stream");
    return stream;
  }

  @Override
  public void onSubscribe(Flow.Subscription s) {
    LOG.log(
        Level.TRACE,
        () -> "PublisherInputStream subscribed, requesting initial " + PREFETCH + " items");
    this.subscription = s;
    pendingRequests.set(PREFETCH);
    s.request(PREFETCH);
  }

  @Override
  public void onNext(ByteBuffer bb) {
    // Decrement pending count - this item was delivered
    int currentPending = pendingRequests.decrementAndGet();

    // Only allocate byte array if buffer has data
    if (bb.hasRemaining()) {
      byte[] b = new byte[bb.remaining()];
      bb.get(b);
      chunksReceived++;
      totalBytesReceived += b.length;
      final long chunks = chunksReceived;
      final long total = totalBytesReceived;
      final int len = b.length;
      LOG.log(
          Level.TRACE,
          () ->
              "Received chunk #"
                  + chunks
                  + ": "
                  + len
                  + " bytes (total: "
                  + total
                  + " bytes, pending: "
                  + currentPending
                  + ")");

      // Non-blocking offer - queue is unbounded, so this always succeeds
      queue.offer(b);
    } else {
      LOG.log(Level.TRACE, () -> "Received empty buffer (pending: " + currentPending + ")");
    }

    // Request more HERE when pending is low - don't wait for consumer!
    // This prevents deadlock where consumer is blocked on queue.take()
    // and publisher has no outstanding requests.
    maybeRequestMore();
  }

  @Override
  public void onError(Throwable t) {
    if (t instanceof RejectedExecutionException) {
      LOG.log(
          Level.TRACE,
          () ->
              "Stream interrupted by shutdown after receiving "
                  + totalBytesReceived
                  + " bytes in "
                  + chunksReceived
                  + " chunks");
      queue.offer(END);
    } else {
      LOG.log(
          Level.ERROR,
          () ->
              "Stream error after receiving "
                  + totalBytesReceived
                  + " bytes in "
                  + chunksReceived
                  + " chunks",
          t);
      queue.offer(t);
    }
  }

  @Override
  public void onComplete() {
    LOG.log(
        Level.DEBUG,
        () ->
            "Stream completed successfully after receiving "
                + totalBytesReceived
                + " bytes in "
                + chunksReceived
                + " chunks");
    queue.offer(END);
  }

  /**
   * Request more data from publisher if pending requests are low. Called from both onNext()
   * (publisher thread) and ensureBuffer() (consumer thread).
   *
   * <p>Uses getAndUpdate for atomic read-modify-write to prevent race conditions where a failed CAS
   * would silently skip requesting more data, potentially causing deadlock.
   */
  private void maybeRequestMore() {
    Flow.Subscription s = this.subscription;
    if (s != null && !closed) {
      // Atomically update pendingRequests: if below threshold, reset to PREFETCH
      // getAndUpdate guarantees we see the actual old value and make the correct decision
      int oldPending =
          pendingRequests.getAndUpdate(pending -> pending < PREFETCH / 2 ? PREFETCH : pending);

      // Only request if we actually updated (old value was below threshold)
      if (oldPending < PREFETCH / 2) {
        int toRequest = PREFETCH - oldPending;
        LOG.log(
            Level.TRACE,
            () ->
                "Requesting "
                    + toRequest
                    + " more items (was "
                    + oldPending
                    + " pending)");
        try {
          s.request(toRequest);
        } catch (RejectedExecutionException e) {
          LOG.log(Level.TRACE, "Executor shutting down, closing stream gracefully");
          close();
        }
      }
    }
  }

  private byte[] ensureBuffer() throws IOException {
    while (!closed && (current == null || idx >= current.length)) {
      // Request more data from publisher before blocking on queue
      maybeRequestMore();

      LOG.log(
          Level.TRACE,
          () ->
              "ensureBuffer waiting on queue.poll() - queue size: "
                  + queue.size()
                  + ", pending: "
                  + pendingRequests.get());
      Object item;
      try {
        // Use poll with timeout to prevent indefinite hangs
        // This catches cases where the publisher stops producing data unexpectedly
        item = queue.poll(QUEUE_POLL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (item == null) {
          // Timeout - no data received within the timeout period
          LOG.log(
              Level.ERROR,
              () ->
                  "PublisherInputStream timed out after "
                      + QUEUE_POLL_TIMEOUT_MINUTES
                      + " minutes waiting for data. "
                      + "Received "
                      + totalBytesReceived
                      + " bytes in "
                      + chunksReceived
                      + " chunks so far. Queue size: "
                      + queue.size()
                      + ", pending requests: "
                      + pendingRequests.get());
          throw new IOException(
              "Timed out waiting for data from publisher after "
                  + QUEUE_POLL_TIMEOUT_MINUTES
                  + " minutes. This may indicate a deadlock in the upstream publisher.");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while waiting for data", e);
      }
      if (item == END) {
        LOG.log(Level.TRACE, "END marker consumed - returning EOF to reader");
        // Re-offer END so subsequent reads also return EOF immediately
        queue.offer(END);
        return null;
      }
      if (item instanceof Throwable t) throw new IOException("Upstream error", t);
      current = (byte[]) item;
      idx = 0;
    }
    return current;
  }

  @Override
  public int read() throws IOException {
    byte[] buf = ensureBuffer();
    if (buf == null) return -1;
    return buf[idx++] & 0xFF;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    byte[] buf = ensureBuffer();
    if (buf == null) return -1; // EOF

    int available = buf.length - idx;
    int toRead = Math.min(len, available);
    System.arraycopy(buf, idx, b, off, toRead);
    idx += toRead;

    // Clear buffer if exhausted, so next call fetches new chunk
    if (idx >= buf.length) {
      current = null;
    }

    return toRead;
  }

  @Override
  public void close() {
    closed = true;
    Flow.Subscription s = this.subscription;
    if (s != null) s.cancel();
    queue.clear();
  }
}
