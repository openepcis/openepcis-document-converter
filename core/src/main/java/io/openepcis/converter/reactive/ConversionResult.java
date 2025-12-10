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

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Encapsulates the result of a document conversion with metadata and convenience methods.
 *
 * <p>This class provides a rich API for consuming conversion results in various ways:
 * <ul>
 *   <li>Stream chunks directly via {@link #content()}</li>
 *   <li>Collect to byte array via {@link #asByteArray()}</li>
 *   <li>Collect to String via {@link #asString()}</li>
 *   <li>Write to OutputStream via {@link #writeTo(OutputStream)}</li>
 * </ul>
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * ConversionResult result = ConversionResult.of(
 *     transformer.convert(source, conversion),
 *     EPCISFormat.JSON_LD,
 *     EPCISVersion.VERSION_2_0_0);
 *
 * // Stream processing
 * result.content()
 *     .subscribe().with(
 *         chunk -> response.write(chunk),
 *         error -> response.fail(error),
 *         () -> response.end());
 *
 * // Collect to String
 * String json = result.asString().await().indefinitely();
 *
 * // Write to file
 * try (OutputStream out = new FileOutputStream("output.json")) {
 *     result.writeTo(out).await().indefinitely();
 * }
 * }</pre>
 *
 * @see ReactiveVersionTransformer
 */
public final class ConversionResult {

  private final Multi<byte[]> content;
  private final EPCISFormat outputFormat;
  private final EPCISVersion outputVersion;

  private ConversionResult(
      Multi<byte[]> content,
      EPCISFormat outputFormat,
      EPCISVersion outputVersion) {
    this.content = Objects.requireNonNull(content, "Content cannot be null");
    this.outputFormat = outputFormat;
    this.outputVersion = outputVersion;
  }

  /**
   * Creates a ConversionResult from a Multi and metadata.
   *
   * @param content the reactive content stream
   * @param outputFormat the output format
   * @param outputVersion the output version
   * @return a new ConversionResult
   */
  public static ConversionResult of(
      Multi<byte[]> content,
      EPCISFormat outputFormat,
      EPCISVersion outputVersion) {
    return new ConversionResult(content, outputFormat, outputVersion);
  }

  /**
   * Creates a ConversionResult from a Multi with unknown format/version.
   *
   * @param content the reactive content stream
   * @return a new ConversionResult
   */
  public static ConversionResult of(Multi<byte[]> content) {
    return new ConversionResult(content, null, null);
  }

  /**
   * Returns the reactive content stream.
   *
   * <p>The content is emitted in 8KB chunks by default.
   *
   * @return Multi emitting byte array chunks
   */
  public Multi<byte[]> content() {
    return content;
  }

  /**
   * Returns the output format.
   *
   * @return the output format, or null if unknown
   */
  public EPCISFormat outputFormat() {
    return outputFormat;
  }

  /**
   * Returns the output version.
   *
   * @return the output version, or null if unknown
   */
  public EPCISVersion outputVersion() {
    return outputVersion;
  }

  /**
   * Collects the content into a single byte array.
   *
   * <p><strong>Warning:</strong> This loads the entire document into memory.
   * For large documents, consider streaming via {@link #content()} instead.
   *
   * @return Uni emitting the complete content as a byte array
   */
  public Uni<byte[]> asByteArray() {
    return content
        .collect().in(
            ByteArrayOutputStream::new,
            (baos, bytes) -> baos.writeBytes(bytes))
        .map(ByteArrayOutputStream::toByteArray);
  }

  /**
   * Collects the content into a String using UTF-8 encoding.
   *
   * <p><strong>Warning:</strong> This loads the entire document into memory.
   * For large documents, consider streaming via {@link #content()} instead.
   *
   * @return Uni emitting the complete content as a String
   */
  public Uni<String> asString() {
    return asString(StandardCharsets.UTF_8);
  }

  /**
   * Collects the content into a String using the specified charset.
   *
   * <p><strong>Warning:</strong> This loads the entire document into memory.
   * For large documents, consider streaming via {@link #content()} instead.
   *
   * @param charset the charset to use for decoding
   * @return Uni emitting the complete content as a String
   */
  public Uni<String> asString(Charset charset) {
    return asByteArray().map(bytes -> new String(bytes, charset));
  }

  /**
   * Writes the content to an OutputStream.
   *
   * <p>The OutputStream is NOT closed after writing. The caller is responsible
   * for closing the stream.
   *
   * @param outputStream the output stream to write to
   * @return Uni that completes when all data has been written
   */
  public Uni<Void> writeTo(OutputStream outputStream) {
    Objects.requireNonNull(outputStream, "OutputStream cannot be null");
    return content
        .onItem().invoke(bytes -> {
          try {
            outputStream.write(bytes);
          } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to write to OutputStream", e);
          }
        })
        .collect().last()
        .replaceWithVoid();
  }

  /**
   * Returns the appropriate Content-Type header value for this result.
   *
   * @return the Content-Type value, or null if format is unknown
   */
  public String contentType() {
    if (outputFormat == null) {
      return null;
    }
    return switch (outputFormat) {
      case XML -> "application/xml";
      case JSON_LD -> "application/ld+json";
    };
  }

  @Override
  public String toString() {
    return "ConversionResult{" +
        "outputFormat=" + outputFormat +
        ", outputVersion=" + outputVersion +
        '}';
  }
}
