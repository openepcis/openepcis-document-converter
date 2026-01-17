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
package io.openepcis.converter;

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.common.GS1FormatSupport;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.converter.json.JsonToXmlConverter;
import io.openepcis.converter.reactive.ReactiveConversionSource;
import io.openepcis.converter.reactive.ReactiveVersionTransformer;
import io.openepcis.converter.util.ChannelUtil;
import io.openepcis.converter.xml.XmlToJsonConverter;
import io.openepcis.converter.xml.XmlVersionTransformer;
import io.openepcis.model.epcis.format.CBVFormat;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import java.io.*;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;

public class VersionTransformer {

  private static final Logger LOG = System.getLogger(VersionTransformer.class.getName());

  private static final String COULD_NOT_WRITE_OR_CLOSE_THE_STREAM =
      "Could not write or close the stream";

  /** Buffer size for PipedInputStream (8KB to match reactive default buffer size) */
  private static final int PIPE_BUFFER_SIZE = 8192;

  /** Maximum duration to wait for conversion completion */
  private static final Duration CONVERSION_TIMEOUT = Duration.ofMinutes(10);
  private final ExecutorService executorService;
  private final XmlVersionTransformer xmlVersionTransformer;
  private final XmlToJsonConverter xmlToJsonConverter;
  private final JsonToXmlConverter jsonToXmlConverter;
  private Optional<BiFunction<Object, List<Object>, Object>> epcisEventMapper = Optional.empty();
  private volatile ReactiveVersionTransformer reactiveVersionTransformer;

  public XmlVersionTransformer getXmlVersionTransformer() {
    return xmlVersionTransformer;
  }

  public XmlToJsonConverter getXmlToJsonConverter() {
    return xmlToJsonConverter;
  }

  public JsonToXmlConverter getJsonToXmlConverter() {
    return jsonToXmlConverter;
  }

  public VersionTransformer(final ExecutorService executorService, final JAXBContext jaxbContext) {
    this.executorService = executorService;
    this.xmlVersionTransformer = XmlVersionTransformer.newInstance(this.executorService);
    this.xmlToJsonConverter = new XmlToJsonConverter(jaxbContext);
    this.jsonToXmlConverter = new JsonToXmlConverter(jaxbContext);
  }

  private VersionTransformer(
      VersionTransformer parent, BiFunction<Object, List<Object>, Object> eventMapper) {
    this.executorService = parent.executorService;
    this.xmlVersionTransformer = parent.xmlVersionTransformer;
    this.jsonToXmlConverter = parent.jsonToXmlConverter.mapWith(eventMapper);
    this.xmlToJsonConverter = parent.xmlToJsonConverter.mapWith(eventMapper);
    this.epcisEventMapper = Optional.ofNullable(eventMapper);
  }

  public VersionTransformer(final ExecutorService executorService) throws JAXBException {
    this.executorService = executorService;
    this.xmlVersionTransformer = XmlVersionTransformer.newInstance(this.executorService);
    this.xmlToJsonConverter = new XmlToJsonConverter();
    this.jsonToXmlConverter = new JsonToXmlConverter();
  }

  private VersionTransformer(
      final VersionTransformer parent,
      final BiFunction<Object, List<Object>, Object> eventMapper,
      final String gs1Extensions) {
    this.executorService = parent.executorService;
    this.xmlVersionTransformer = parent.xmlVersionTransformer;
    this.jsonToXmlConverter = parent.jsonToXmlConverter.mapWith(eventMapper);
    this.xmlToJsonConverter = parent.xmlToJsonConverter.mapWith(eventMapper);
    this.epcisEventMapper = Optional.ofNullable(eventMapper);
  }

  public VersionTransformer() throws JAXBException {
    this(Executors.newFixedThreadPool(8));
  }

  public final InputStream convert(
      final InputStream inputStream,
      final Function<Conversion.StartStage, Conversion.BuildStage> fn)
      throws UnsupportedOperationException, IOException {
    return convert(inputStream, fn.apply(Conversion.builder()).build());
  }

  /**
   * Method with autodetect EPCIS version from inputStream
   *
   * @param in EPCIS document in either application/xml or application/json format as a InputStream
   * @param conversion Conversion object with required fields.
   * @return returns the converted EPCIS document as InputStream which can be used for further
   *     processing
   * @throws UnsupportedOperationException if user is trying to convert different version other than
   *     specified then throw the error
   */
  public final InputStream convert(final InputStream in, final Conversion conversion)
      throws UnsupportedOperationException, IOException {

    // Use larger buffer for BufferedInputStream to match mark limit in version detection
    final BufferedInputStream inputDocument = new BufferedInputStream(in, 1024 * 1024);
    EPCISVersion fromVersion;
    try{
      // Checking if mediaType is JSON_LD, and detecting version conditionally
      fromVersion = EPCISFormat.JSON_LD.equals(conversion.fromMediaType()) ? EPCISVersion.VERSION_2_0_0 : versionDetector(inputDocument, conversion);
    }catch (Exception e){
        throw new FormatConverterException(e.getMessage(), e);
    }

    // After version detection, the stream is reset. Pass it directly to conversion.
    // No intermediate pipe needed since version detection uses mark/reset properly.
    final Conversion conversionToPerform =
        Conversion.of(
            conversion.fromMediaType(),
            fromVersion,
            conversion.toMediaType(),
            conversion.toVersion(),
            conversion.generateGS1CompliantDocument().orElse(null),
            conversion.onFailure().orElse(null)
        );
    return performConversion(inputDocument, conversionToPerform);
  }

  /**
   * Method with autodetect EPCIS version from inputStream without knowing about Conversion
   *
   * @param epcisDocument EPCIS document in application/xml or application/json format as a
   *     InputStream
   * @return returns the detected version with read prescan details for merging back again.
   * @throws IOException if unable to read the document
   */
  public final EPCISVersion versionDetector(final BufferedInputStream epcisDocument)
      throws IOException {
    return versionDetector(epcisDocument, Conversion.UNKNOWN);
  }

  /**
   * Method with autodetect EPCIS version from inputStream
   *
   * @param epcisDocument EPCIS document in application/xml or application/json format as a
   *     InputStream
   * @param conversion Conversion operation
   * @return returns the detected version with read prescan details for merging back again.
   * @throws IOException if unable to read the document
   */
  public final EPCISVersion versionDetector(
      final BufferedInputStream epcisDocument, final Conversion conversion) throws IOException {
    if (conversion.fromVersion() != null) {
      return conversion.fromVersion();
    }

    final String preScanVersion = AttributePreScanUtil.scanSchemaVersion(epcisDocument);

    if (preScanVersion.isEmpty()) {
      throw new FormatConverterException(
          "Unable to detect EPCIS schemaVersion for given document, please check the document again");
    }

    return EPCISVersion.fromString(preScanVersion)
        .orElseThrow(
            () ->
                new FormatConverterException(
                    String.format(
                        "Provided document contains unsupported EPCIS document version %s",
                        preScanVersion)));
  }

  /**
   * API method to accept EPCIS document input and transform it to corresponding document based on
   * user specification.
   *
   * <p>All conversions are delegated to the {@link ReactiveVersionTransformer} which provides
   * non-blocking, backpressure-aware processing. The result is bridged back to an InputStream
   * using a piped stream with data pumping on the executor thread.
   *
   * @param inputDocument EPCIS document in either application/xml or application/json format as a
   *     InputStream
   * @return returns the converted document as InputStream which can be used for further processing
   * @throws UnsupportedOperationException if user is trying to convert different version other than
   *     specified then throw the error
   * @throws IOException If any exception occur during the conversion then throw the error
   */
  public final InputStream performConversion(
      final InputStream inputDocument, final Conversion conversion)
      throws UnsupportedOperationException, IOException {
    // Delegate all conversions to the reactive API
    return convertViaReactiveApi(inputDocument, conversion);
  }

  /**
   * Converts using the reactive API.
   * Collects output into a ByteArrayOutputStream for reliability,
   * then wraps it as a ByteArrayInputStream.
   */
  private InputStream convertViaReactiveApi(final InputStream inputDocument, final Conversion conversion) {
    // Determine effective event mapper based on conversion settings
    BiFunction<Object, List<Object>, Object> effectiveMapper = epcisEventMapper.orElse(null);

    // If a mapper is already set via mapWith(), use it directly without adding internal GS1 conversion.
    // This allows header-based format preferences to take full control.
    // Only apply internal GS1 conversion defaults when no external mapper is configured.
    if (effectiveMapper == null) {
      // Default: true for XML 2.0 and JSON 2.0, false for XML 1.2 (Digital Link URI not supported)
      final boolean isXml12Target = EPCISFormat.XML.equals(conversion.toMediaType()) && EPCISVersion.VERSION_1_2_0.equals(conversion.toVersion());
      final boolean defaultGS1Conversion = !isXml12Target;

      final Boolean gs1Compliant = conversion.generateGS1CompliantDocument().orElse(defaultGS1Conversion);
      if (Boolean.TRUE.equals(gs1Compliant)) {
        // Determine CBV format based on target media type:
        // - JSON-LD: Use bare strings (No_Preference) as JSON-LD context provides expansion
        // - XML: Use Web URI format
        final boolean isJsonTarget = EPCISFormat.JSON_LD.equals(conversion.toMediaType());
        final String cbvFormat = isJsonTarget ? CBVFormat.No_Preference.getCbvFormat() : CBVFormat.Always_Web_URI.getCbvFormat();

        // Create FormatPreference for GS1 Digital Link conversion
        final FormatPreference formatPreference = new FormatPreference(EPCFormat.Always_GS1_Digital_Link.getEPCFormat(), cbvFormat);
        effectiveMapper = GS1FormatSupport.createMapper(formatPreference);
      }
    }

    // Build reactive transformer with the effective mapper
    final ReactiveVersionTransformer reactiveTransformer = ReactiveVersionTransformer.builder()
        .eventMapper(effectiveMapper)
        .build();

    // Create reactive source directly from InputStream
    final ReactiveConversionSource source = ReactiveConversionSource.fromInputStream(inputDocument);

    // Collect all output into a ByteArrayOutputStream
    final java.io.ByteArrayOutputStream outputBuffer = new java.io.ByteArrayOutputStream();

    try {
      reactiveTransformer.convert(source, conversion)
          .onItem().invoke(bytes -> {
            try {
              outputBuffer.write(bytes);
            } catch (IOException e) {
              throw new FormatConverterException("Failed to write to buffer", e);
            }
          })
          .onFailure().invoke(conversion::fail)
          .collect().last()
          .await().atMost(CONVERSION_TIMEOUT);
    } catch (Exception e) {
      conversion.fail(e);
      throw new FormatConverterException("Conversion failed: " + e.getMessage(), e);
    }

    return new java.io.ByteArrayInputStream(outputBuffer.toByteArray());
  }

  private void closeQuietly(Closeable closeable) {
    try {
      closeable.close();
    } catch (IOException e) {
      LOG.log(Level.WARNING, COULD_NOT_WRITE_OR_CLOSE_THE_STREAM, e);
    }
  }

  public final VersionTransformer mapWith(final BiFunction<Object, List<Object>, Object> mapper) {
    return new VersionTransformer(this, mapper);
  }

  public final VersionTransformer mapWith(
      final BiFunction<Object, List<Object>, Object> mapper, final String gs1Extensions) {
    return new VersionTransformer(this, mapper, gs1Extensions);
  }

  /**
   * Returns the reactive version transformer for non-blocking, backpressure-aware conversions.
   *
   * <p>The reactive transformer supports:
   * <ul>
   *   <li>{@code Flow.Publisher<ByteBuffer>} input for true non-blocking I/O</li>
   *   <li>{@code Multi<byte[]>} output for document-level streaming</li>
   *   <li>{@code Multi<EPCISEvent>} output for event-level streaming</li>
   *   <li>Full backpressure support throughout the pipeline</li>
   * </ul>
   *
   * <p>Usage example:
   * <pre>{@code
   * Flow.Publisher<ByteBuffer> httpBody = ...;
   * Multi<byte[]> result = versionTransformer.reactive()
   *     .convert(httpBody, Conversion.builder()
   *         .fromMediaType(EPCISFormat.XML)
   *         .toMediaType(EPCISFormat.JSON_LD)
   *         .toVersion(EPCISVersion.VERSION_2_0_0)
   *         .build());
   * }</pre>
   *
   * @return the reactive version transformer
   */
  public ReactiveVersionTransformer reactive() {
    ReactiveVersionTransformer result = reactiveVersionTransformer;
    if (result == null) {
      synchronized (this) {
        result = reactiveVersionTransformer;
        if (result == null) {
          result = ReactiveVersionTransformer.builder()
              .eventMapper(epcisEventMapper.orElse(null))
              .blockingExecutor(executorService)
              .build();
          reactiveVersionTransformer = result;
        }
      }
    }
    return epcisEventMapper.isPresent()
        ? result.mapWith(epcisEventMapper.get())
        : result;
  }

  // For API backward compatibility
  @Deprecated(forRemoval = true)
  public final InputStream convert(
      final InputStream inputDocument,
      final EPCISFormat fromMediaType,
      final EPCISFormat toMediaType,
      final EPCISVersion toVersion,
      final boolean generateGS1CompliantDocument)
      throws UnsupportedOperationException, IOException {
    return convert(
        inputDocument,
        Conversion.builder()
            .generateGS1CompliantDocument(generateGS1CompliantDocument)
            .fromMediaType(fromMediaType)
            .toMediaType(toMediaType)
            .toVersion(toVersion)
            .build());
  }

  @Deprecated(forRemoval = true)
  public final InputStream convert(
      final InputStream inputDocument,
      final EPCISFormat mediaType,
      final EPCISVersion fromVersion,
      final EPCISVersion toVersion,
      final boolean generateGS1CompliantDocument)
      throws UnsupportedOperationException, IOException {
    return convert(
        inputDocument,
        Conversion.builder()
            .generateGS1CompliantDocument(generateGS1CompliantDocument)
            .fromMediaType(mediaType)
            .toVersion(toVersion)
            .build());
  }

  @Deprecated(forRemoval = true)
  public final InputStream convert(
      final InputStream inputDocument,
      final EPCISFormat fromMediaType,
      final EPCISVersion fromVersion,
      final EPCISFormat toMediaType,
      final EPCISVersion toVersion,
      final boolean generateGS1CompliantDocument)
      throws UnsupportedOperationException, IOException {
    return convert(
        inputDocument,
        Conversion.of(
            fromMediaType, fromVersion, toMediaType, toVersion, generateGS1CompliantDocument));
  }

  @Deprecated(forRemoval = true)
  public final InputStream convert(
      final InputStream inputDocument,
      final EPCISFormat fromMediaType,
      final EPCISFormat toMediaType,
      final EPCISVersion toVersion)
      throws UnsupportedOperationException, IOException {
    return convert(
        inputDocument,
        Conversion.builder()
            .fromMediaType(fromMediaType)
            .toMediaType(toMediaType)
            .toVersion(toVersion)
            .build());
  }

  @Deprecated(forRemoval = true)
  public final InputStream convert(
      final InputStream inputDocument,
      final EPCISFormat mediaType,
      final EPCISVersion fromVersion,
      final EPCISVersion toVersion)
      throws UnsupportedOperationException, IOException {
    return convert(
        inputDocument, Conversion.builder().fromMediaType(mediaType).toVersion(toVersion).build());
  }

  @Deprecated(forRemoval = true)
  public final InputStream convert(
      final InputStream inputDocument,
      final EPCISFormat fromMediaType,
      final EPCISVersion fromVersion,
      final EPCISFormat toMediaType,
      final EPCISVersion toVersion)
      throws UnsupportedOperationException, IOException {
    return convert(
        inputDocument, Conversion.of(fromMediaType, fromVersion, toMediaType, toVersion));
  }
}
