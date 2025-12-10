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

import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.Conversion;
import io.openepcis.converter.VersionTransformerFeature;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.reactive.util.ReactiveSource;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * Reactive XSLT transformer for converting between EPCIS 1.2 and 2.0 XML formats.
 *
 * <p>XSLT transformation is inherently blocking, so this class wraps the transformation
 * in a Uni/Multi context with proper error handling.
 *
 * <p><strong>Architecture:</strong>
 * <pre>
 * Flow.Publisher&lt;ByteBuffer&gt; (XML 1.2 or 2.0 input)
 *         |
 *         v
 * ByteArrayOutputStream (buffering)
 *         |
 *         v
 * XSLT Transformation (blocking, uses pre-compiled translets)
 *         |
 *         v
 * Multi&lt;byte[]&gt; (transformed XML in 8KB chunks)
 * </pre>
 *
 * <p><strong>Thread safety:</strong> This class is thread-safe. The underlying XSLT
 * Templates are immutable and can be shared across threads.
 */
public class ReactiveXmlVersionTransformer {

  private static final int DEFAULT_BUFFER_SIZE = 8192;

  private final Templates from12To20;
  private final Templates from20To12;

  /**
   * Creates a new transformer with default XSLT templates.
   *
   * @throws RuntimeException if templates cannot be loaded
   */
  public ReactiveXmlVersionTransformer() {
    try {
      TransformerFactory transformerFactory = TransformerFactory.newInstance(
          "org.apache.xalan.xsltc.trax.TransformerFactoryImpl",
          Thread.currentThread().getContextClassLoader());

      // Load 2.0 -> 1.2 template
      transformerFactory.setAttribute("package-name", "io.openepcis.converter.translet");
      transformerFactory.setAttribute("translet-name", "From20To12");
      transformerFactory.setAttribute("use-classpath", true);
      from20To12 = transformerFactory.newTemplates(
          new StreamSource(
              Thread.currentThread().getContextClassLoader()
                  .getResourceAsStream("xalan-conversion/convert-2.0-to-1.2.xsl")));

      // Load 1.2 -> 2.0 template
      transformerFactory = TransformerFactory.newInstance(
          "org.apache.xalan.xsltc.trax.TransformerFactoryImpl",
          Thread.currentThread().getContextClassLoader());
      transformerFactory.setAttribute("package-name", "io.openepcis.converter.translet");
      transformerFactory.setAttribute("translet-name", "From12To20");
      transformerFactory.setAttribute("use-classpath", true);
      from12To20 = transformerFactory.newTemplates(
          new StreamSource(
              Thread.currentThread().getContextClassLoader()
                  .getResourceAsStream("xalan-conversion/convert-1.2-to-2.0.xsl")));

    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize XSLT templates", e);
    }
  }

  /**
   * Transforms XML from one EPCIS version to another.
   *
   * @param source the reactive XML input source
   * @param conversion the conversion specification
   * @return Multi emitting transformed XML in chunks
   */
  public Multi<byte[]> transform(Flow.Publisher<ByteBuffer> source, Conversion conversion) {
    return transform(ReactiveSource.fromPublisher(source), conversion);
  }

  /**
   * Transforms XML from one EPCIS version to another.
   *
   * @param source the conversion source
   * @param conversion the conversion specification
   * @return Multi emitting transformed XML in chunks
   */
  public Multi<byte[]> transform(ReactiveSource source, Conversion conversion) {
    Objects.requireNonNull(source, "Source cannot be null");
    Objects.requireNonNull(conversion, "Conversion cannot be null");

    EPCISVersion fromVersion = conversion.fromVersion();
    EPCISVersion toVersion = conversion.toVersion();

    // If same version, pass through unchanged
    if (fromVersion != null && fromVersion.equals(toVersion)) {
      return passThrough(source);
    }

    // Collect all bytes first (XSLT needs complete document), then transform
    return source.toMulti()
        .onItem().transform(buffer -> {
          byte[] bytes = new byte[buffer.remaining()];
          buffer.get(bytes);
          return bytes;
        })
        .collect().in(ByteArrayOutputStream::new, (baos, bytes) -> {
          try {
            baos.write(bytes);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        })
        .onItem().transform(baos -> {
          try {
            byte[] inputBytes = baos.toByteArray();
            ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();

            if (EPCISVersion.VERSION_1_2_0.equals(fromVersion)
                && EPCISVersion.VERSION_2_0_0.equals(toVersion)) {
              transform12To20(inputBytes, outputBuffer);
            } else if ((EPCISVersion.VERSION_2_0_0.equals(fromVersion) || EPCISVersion.VERSION_1_1_0.equals(fromVersion))
                && EPCISVersion.VERSION_1_2_0.equals(toVersion)) {
              transform20To12(inputBytes, outputBuffer, conversion);
            } else if (EPCISVersion.VERSION_1_1_0.equals(fromVersion)
                && EPCISVersion.VERSION_2_0_0.equals(toVersion)) {
              transform12To20(inputBytes, outputBuffer);
            } else {
              throw new UnsupportedOperationException(
                  "Unsupported version transformation: " + fromVersion + " -> " + toVersion);
            }

            return outputBuffer.toByteArray();
          } catch (Exception e) {
            throw new FormatConverterException("XSLT transformation failed", e);
          }
        })
        .onItem().transformToMulti(this::chunkBytes);
  }

  /**
   * Transforms EPCIS 1.2 XML to 2.0.
   *
   * @param source the conversion source
   * @return Multi emitting transformed XML in chunks
   */
  public Multi<byte[]> transform12To20(ReactiveSource source) {
    return transform(source, Conversion.of(
        null, EPCISVersion.VERSION_1_2_0, null, EPCISVersion.VERSION_2_0_0));
  }

  /**
   * Transforms EPCIS 2.0 XML to 1.2.
   *
   * @param source the conversion source
   * @param enabledFeatures list of enabled transformation features
   * @return Multi emitting transformed XML in chunks
   */
  public Multi<byte[]> transform20To12(
      ReactiveSource source,
      List<VersionTransformerFeature> enabledFeatures) {
    Conversion conversion = Conversion.builder()
        .generateGS1CompliantDocument(true)
        .fromMediaType(null)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toVersion(EPCISVersion.VERSION_1_2_0)
        .build();
    return transform(source, conversion);
  }

  // ==================== Internal Methods ====================

  private Multi<byte[]> passThrough(ReactiveSource source) {
    return source.toMulti()
        .onItem().transform(buffer -> {
          byte[] bytes = new byte[buffer.remaining()];
          buffer.get(bytes);
          return bytes;
        });
  }

  private void transform12To20(byte[] input, ByteArrayOutputStream output) throws Exception {
    Transformer transformer = from12To20.newTransformer();
    transformer.transform(
        new StreamSource(new ByteArrayInputStream(input)),
        new StreamResult(new BufferedOutputStream(output)));
  }

  private void transform20To12(byte[] input, ByteArrayOutputStream output, Conversion conversion)
      throws Exception {
    Transformer transformer = from20To12.newTransformer();

    List<VersionTransformerFeature> features = VersionTransformerFeature.enabledFeatures(conversion);

    transformer.setParameter("includeAssociationEvent",
        features.contains(VersionTransformerFeature.EPCIS_1_2_0_INCLUDE_ASSOCIATION_EVENT)
            ? "yes" : "no");
    transformer.setParameter("includePersistentDisposition",
        features.contains(VersionTransformerFeature.EPCIS_1_2_0_INCLUDE_PERSISTENT_DISPOSITION)
            ? "yes" : "no");
    transformer.setParameter("includeSensorElementList",
        features.contains(VersionTransformerFeature.EPCIS_1_2_0_INCLUDE_SENSOR_ELEMENT_LIST)
            ? "yes" : "no");

    transformer.transform(
        new StreamSource(new ByteArrayInputStream(input)),
        new StreamResult(new BufferedOutputStream(output)));
  }

  private Multi<byte[]> chunkBytes(byte[] data) {
    return Multi.createFrom().emitter(emitter -> {
      int offset = 0;
      while (offset < data.length) {
        int chunkSize = Math.min(DEFAULT_BUFFER_SIZE, data.length - offset);
        byte[] chunk = new byte[chunkSize];
        System.arraycopy(data, offset, chunk, 0, chunkSize);
        emitter.emit(chunk);
        offset += chunkSize;
      }
      emitter.complete();
    });
  }
}
