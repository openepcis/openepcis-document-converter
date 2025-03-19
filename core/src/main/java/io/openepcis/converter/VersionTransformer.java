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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.collector.EventHandler;
import io.openepcis.converter.collector.JsonEPCISEventCollector;
import io.openepcis.converter.collector.XmlEPCISEventCollector;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.converter.json.JSONEventValueTransformer;
import io.openepcis.converter.json.JsonToXmlConverter;
import io.openepcis.converter.util.ChannelUtil;
import io.openepcis.converter.xml.ProblemResponseBodyMarshaller;
import io.openepcis.converter.xml.XMLEventValueTransformer;
import io.openepcis.converter.xml.XmlToJsonConverter;
import io.openepcis.converter.xml.XmlVersionTransformer;
import io.openepcis.model.rest.ProblemResponseBody;
import io.smallrye.mutiny.Uni;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import java.io.*;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VersionTransformer {

  public static final String COULD_NOT_WRITE_OR_CLOSE_THE_STREAM =
      "Could not write or close the stream";
  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .setSerializationInclusion(JsonInclude.Include.NON_NULL)
          .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
  private final ExecutorService executorService;

  public XmlVersionTransformer getXmlVersionTransformer() {
    return xmlVersionTransformer;
  }

  public XmlToJsonConverter getXmlToJsonConverter() {
    return xmlToJsonConverter;
  }

  public JsonToXmlConverter getJsonToXmlConverter() {
    return jsonToXmlConverter;
  }

  private final XmlVersionTransformer xmlVersionTransformer;
  private final XmlToJsonConverter xmlToJsonConverter;
  private final JsonToXmlConverter jsonToXmlConverter;
  private final JSONEventValueTransformer jsonEventValueTransformer;
  private final XMLEventValueTransformer xmlEventValueTransformer;
  private Optional<BiFunction<Object, List<Object>, Object>> epcisEventMapper = Optional.empty();
  private String gs1Extensions;

  public VersionTransformer(final ExecutorService executorService, final JAXBContext jaxbContext) {
    this.executorService = executorService;
    this.xmlVersionTransformer = XmlVersionTransformer.newInstance(this.executorService);
    this.xmlToJsonConverter = new XmlToJsonConverter(jaxbContext);
    this.jsonToXmlConverter = new JsonToXmlConverter(jaxbContext);
    this.jsonEventValueTransformer = new JSONEventValueTransformer();
    this.xmlEventValueTransformer = new XMLEventValueTransformer(jaxbContext);
  }

  private VersionTransformer(
      VersionTransformer parent, BiFunction<Object, List<Object>, Object> eventMapper) {
    this.executorService = parent.executorService;
    this.xmlVersionTransformer = parent.xmlVersionTransformer;
    this.jsonToXmlConverter = parent.jsonToXmlConverter.mapWith(eventMapper);
    this.xmlToJsonConverter = parent.xmlToJsonConverter.mapWith(eventMapper);
    this.jsonEventValueTransformer = parent.jsonEventValueTransformer.mapWith(eventMapper);
    this.xmlEventValueTransformer = parent.xmlEventValueTransformer.mapWith(eventMapper);
    this.epcisEventMapper = Optional.ofNullable(eventMapper);
  }

  public VersionTransformer(final ExecutorService executorService) throws JAXBException {
    this.executorService = executorService;
    this.xmlVersionTransformer = XmlVersionTransformer.newInstance(this.executorService);
    this.xmlToJsonConverter = new XmlToJsonConverter();
    this.jsonToXmlConverter = new JsonToXmlConverter();
    this.jsonEventValueTransformer = new JSONEventValueTransformer();
    this.xmlEventValueTransformer = new XMLEventValueTransformer();
  }

  private VersionTransformer(
      final VersionTransformer parent,
      final BiFunction<Object, List<Object>, Object> eventMapper,
      final String gs1Extensions) {
    this.executorService = parent.executorService;
    this.xmlVersionTransformer = parent.xmlVersionTransformer;
    this.jsonToXmlConverter = parent.jsonToXmlConverter.mapWith(eventMapper);
    this.xmlToJsonConverter = parent.xmlToJsonConverter.mapWith(eventMapper);
    this.jsonEventValueTransformer = parent.jsonEventValueTransformer.mapWith(eventMapper);
    this.xmlEventValueTransformer = parent.xmlEventValueTransformer.mapWith(eventMapper);
    this.epcisEventMapper = Optional.ofNullable(eventMapper);
    this.gs1Extensions = gs1Extensions;
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

    final BufferedInputStream inputDocument = new BufferedInputStream(in);
    // Checking if mediaType is JSON_LD, and detecting version conditionally
    EPCISVersion fromVersion =
        EPCISFormat.JSON_LD.equals(conversion.fromMediaType())
            ? EPCISVersion.VERSION_2_0_0
            : versionDetector(inputDocument, conversion);
    InputStream inputStream = inputDocument;
    // If version detected, result won't be null, thus do InputStream operations
    final PipedInputStream pipe = new PipedInputStream();
    final AtomicBoolean pipeConnected = new AtomicBoolean(false);
    executorService.execute(
        () -> {
          final PipedOutputStream pipedOutputStream = new PipedOutputStream();
          try (pipedOutputStream) {
            pipe.connect(pipedOutputStream);
            pipeConnected.set(true);
            ChannelUtil.copy(inputDocument, pipedOutputStream);
          } catch (Exception e) {
            throw new FormatConverterException(
                "Exception occurred during reading of schema version from input document : "
                    + e.getMessage(),
                e);
          }
        });
    while (!pipeConnected.get()) {
      Thread.yield();
    }
    inputStream = pipe;

    final Conversion conversionToPerform =
        Conversion.of(
            conversion.fromMediaType(),
            fromVersion,
            conversion.toMediaType(),
            conversion.toVersion(),
            conversion.generateGS1CompliantDocument().orElse(null));
    return performConversion(inputStream, conversionToPerform);
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
    // If input fromVersion and the required output toVersion is same then return the same input.
    if (EPCISFormat.XML.equals(conversion.fromMediaType())
        && EPCISFormat.XML.equals(conversion.toMediaType())) {

      if (conversion.toVersion().equals(EPCISVersion.VERSION_1_2_0)) {
        InputStream streamWithPreferences =
            conversion.fromVersion().equals(EPCISVersion.VERSION_2_0_0)
                ? fromXmlToXml(inputDocument)
                : fromXmlToXml(xmlVersionTransformer.xmlConverter(inputDocument, conversion));
        return xmlVersionTransformer.xmlConverter(streamWithPreferences, conversion);
      } else {
        return conversion.fromVersion().equals(EPCISVersion.VERSION_2_0_0)
            ? fromXmlToXml(inputDocument)
            : fromXmlToXml(xmlVersionTransformer.xmlConverter(inputDocument, conversion));
      }
    } else if (EPCISFormat.JSON_LD.equals(conversion.fromMediaType())
        && EPCISFormat.XML.equals(conversion.toMediaType())
        && EPCISVersion.VERSION_2_0_0.equals(conversion.fromVersion())
        && EPCISVersion.VERSION_2_0_0.equals(conversion.toVersion())) {
      // If fromMedia is json and toMedia is xml and both versions are 2.0
      return toXml(inputDocument);
    } else if (EPCISFormat.JSON_LD.equals(conversion.fromMediaType())
        && EPCISFormat.XML.equals(conversion.toMediaType())
        && EPCISVersion.VERSION_2_0_0.equals(conversion.fromVersion())
        && EPCISVersion.VERSION_1_2_0.equals(conversion.toVersion())) {
      // If fromMedia is json and toMedia is xml and fromVersion is 2.0 and toVersion is 1.2
      return xmlVersionTransformer.xmlConverter(toXml(inputDocument), conversion);
    } else if (EPCISFormat.XML.equals(conversion.fromMediaType())
        && EPCISFormat.JSON_LD.equals(conversion.toMediaType())
        && EPCISVersion.VERSION_2_0_0.equals(conversion.fromVersion())
        && EPCISVersion.VERSION_2_0_0.equals(conversion.toVersion())) {
      // If fromMedia is xml and toMedia is json and both versions are 2.0 convert xml->json
      return toJson(inputDocument);
    } else if (EPCISFormat.XML.equals(conversion.fromMediaType())
        && EPCISFormat.JSON_LD.equals(conversion.toMediaType())
        && EPCISVersion.VERSION_1_2_0.equals(conversion.fromVersion())
        && EPCISVersion.VERSION_2_0_0.equals(conversion.toVersion())) {
      // If fromMedia is xml and toMedia is json and fromVersion is 1.2, toVersion 2.0 then convert
      // xml->2.0 and then to JSON
      return toJson(xmlVersionTransformer.xmlConverter(inputDocument, conversion));
    } else if (EPCISFormat.JSON_LD.equals(conversion.fromMediaType())
        && EPCISFormat.JSON_LD.equals(conversion.toMediaType())
        && EPCISVersion.VERSION_2_0_0.equals(conversion.fromVersion())
        && EPCISVersion.VERSION_2_0_0.equals(conversion.toVersion())) {
      // If fromMedia is json and toMedia is xml and fromVersion is 2.0 and toVersion is 1.2
      return fromJsonToJson(inputDocument);
    } else {
      throw new UnsupportedOperationException(
          "Requested conversion is not supported, Please check provided MediaType/Version and try again");
    }
  }

  // Private method to convert the JSON 2.0 document -> XML 2.0 and return it as InputStream
  private InputStream toXml(final InputStream inputDocument) {
    try {
      final PipedOutputStream xmlOutputStream = new PipedOutputStream();
      final PipedInputStream convertedDocument = new PipedInputStream(xmlOutputStream);

      executorService.execute(
          () -> {
            try (final EventHandler<? extends XmlEPCISEventCollector> handler =
                new EventHandler(new XmlEPCISEventCollector(xmlOutputStream)); ) {
              jsonToXmlConverter.convert(inputDocument, handler);
              xmlOutputStream.close();
            } catch (Exception e) {
              try {
                ProblemResponseBodyMarshaller.getMarshaller()
                    .marshal(ProblemResponseBody.fromException(e), xmlOutputStream);
                xmlOutputStream.close();
              } catch (IOException ioe) {
                log.warn(COULD_NOT_WRITE_OR_CLOSE_THE_STREAM, ioe);
              } catch (JAXBException ex) {
                throw new RuntimeException(ex);
              }
            }
          });

      return convertedDocument;
    } catch (Exception e) {
      throw new FormatConverterException(
          "Exception occurred during the conversion of JSON 2.0 document to XML 2.0 document using PipedInputStream : "
              + e.getMessage(),
          e);
    }
  }

  // Private method to convert the XML 2.0 document -> JSON 2.0 document and return as InputStream
  private InputStream toJson(final InputStream inputDocument) {
    try {
      final PipedOutputStream jsonOutputStream = new PipedOutputStream();
      final InputStream convertedDocument = new PipedInputStream(jsonOutputStream);
      return Uni.createFrom()
          .<InputStream>emitter(
              em -> {
                executorService.execute(
                    () -> {
                      try (final EventHandler<? extends JsonEPCISEventCollector> handler =
                          new EventHandler(new JsonEPCISEventCollector(jsonOutputStream)); ) {

                        xmlToJsonConverter.convert(
                            new BufferedInputStream(inputDocument) {
                              long wasRead = 0l;
                              boolean active = false;

                              private int submitOnRead(int read) {
                                if (active) {
                                  return read;
                                }
                                wasRead += read;
                                if (wasRead >= 64) {
                                  active = true;
                                  em.complete(convertedDocument);
                                }
                                return read;
                              }

                              @Override
                              public void close() throws IOException {
                                try {
                                  super.close();
                                  if (!active) {
                                    em.complete(convertedDocument);
                                  }
                                } catch (IOException e) {
                                  if (!active) {
                                    em.fail(e);
                                  }
                                  throw e;
                                }
                              }

                              @Override
                              public int read() throws IOException {
                                submitOnRead(1);
                                return super.read();
                              }

                              @Override
                              public int read(byte[] b, int off, int len) throws IOException {
                                return submitOnRead(super.read(b, off, len));
                              }
                            },
                            handler);
                      } catch (Exception e) {
                        em.fail(e);
                        try {
                          jsonOutputStream.write(
                              objectMapper.writeValueAsBytes(ProblemResponseBody.fromException(e)));
                          jsonOutputStream.close();
                        } catch (IOException ioe) {
                          log.warn(COULD_NOT_WRITE_OR_CLOSE_THE_STREAM, ioe);
                        }
                      }
                    });
              })
          .emitOn(executorService)
          .await()
          .atMost(Duration.ofMillis(5000));

    } catch (Exception e) {
      throw new FormatConverterException(
          "Exception occurred during the conversion of XML 2.0 document to JSON 2.0 document using PipedInputStream  : "
              + e.getMessage(),
          e);
    }
  }

  // Private method to convert the JSON/JSON LD 2.0 document -> JSON 2.0 document and return as
  // InputStream
  private InputStream fromJsonToJson(final InputStream inputDocument) {
    try {
      final PipedOutputStream jsonOutputStream = new PipedOutputStream();

      final InputStream convertedDocument = new PipedInputStream(jsonOutputStream);

      executorService.execute(
          () -> {
            try (final EventHandler<? extends JsonEPCISEventCollector> handler =
                new EventHandler(new JsonEPCISEventCollector(jsonOutputStream)); ) {
              jsonEventValueTransformer.convert(inputDocument, handler);
            } catch (Exception e) {
              try {
                jsonOutputStream.write(
                    objectMapper.writeValueAsBytes(ProblemResponseBody.fromException(e)));
                jsonOutputStream.close();
              } catch (IOException ioe) {
                log.warn(COULD_NOT_WRITE_OR_CLOSE_THE_STREAM, ioe);
              }
            }
          });
      return convertedDocument;
    } catch (Exception e) {
      throw new FormatConverterException(
          "Exception occurred during the conversion of XML 2.0 document to JSON 2.0 document using PipedInputStream  : "
              + e.getMessage(),
          e);
    }
  }

  // Private method to convert the XML 2.0 document -> JSON 2.0 document and return as InputStream
  private InputStream fromXmlToXml(final InputStream inputDocument) {
    try {
      final PipedOutputStream xmlOutputStream = new PipedOutputStream();

      final PipedInputStream convertedDocument = new PipedInputStream(xmlOutputStream);

      executorService.execute(
          () -> {
            try (final EventHandler<? extends XmlEPCISEventCollector> handler =
                new EventHandler(new XmlEPCISEventCollector(xmlOutputStream)); ) {
              xmlEventValueTransformer.convert(inputDocument, handler);
              xmlOutputStream.close();
            } catch (Exception e) {
              try {
                ProblemResponseBodyMarshaller.getMarshaller()
                    .marshal(ProblemResponseBody.fromException(e), xmlOutputStream);
                xmlOutputStream.close();
              } catch (IOException ioe) {
                log.warn(COULD_NOT_WRITE_OR_CLOSE_THE_STREAM, ioe);
              } catch (JAXBException ex) {
                throw new RuntimeException(ex);
              }
            }
          });
      return convertedDocument;
    } catch (Exception e) {
      throw new FormatConverterException(
          "Exception occurred during the conversion of JSON 2.0 document to XML 2.0 document using PipedInputStream : "
              + e.getMessage(),
          e);
    }
  }

  public final VersionTransformer mapWith(final BiFunction<Object, List<Object>, Object> mapper) {
    return new VersionTransformer(this, mapper);
  }

  public final VersionTransformer mapWith(
      final BiFunction<Object, List<Object>, Object> mapper, final String gs1Extensions) {
    return new VersionTransformer(this, mapper, gs1Extensions);
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
