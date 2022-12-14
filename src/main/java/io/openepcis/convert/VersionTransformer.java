package io.openepcis.convert;

import io.openepcis.convert.collector.EventHandler;
import io.openepcis.convert.collector.JsonEPCISEventCollector;
import io.openepcis.convert.collector.XmlEPCISEventCollector;
import io.openepcis.convert.exception.FormatConverterException;
import io.openepcis.convert.json.JsonToXmlConverter;
import io.openepcis.convert.util.ChannelUtil;
import io.openepcis.convert.xml.XmlToJsonConverter;
import io.openepcis.convert.xml.XmlVersionTransformer;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VersionTransformer {

  private final ExecutorService executorService;
  private final XmlVersionTransformer xmlVersionTransformer;
  private final XmlToJsonConverter xmlToJsonConverter;
  private final JsonToXmlConverter jsonToXmlConverter;

  public VersionTransformer(final ExecutorService executorService, final JAXBContext jaxbContext) {
    this.executorService = executorService;
    this.xmlVersionTransformer = new XmlVersionTransformer(this.executorService);
    this.xmlToJsonConverter = new XmlToJsonConverter(jaxbContext);
    this.jsonToXmlConverter = new JsonToXmlConverter(jaxbContext);
  }

  public VersionTransformer(final ExecutorService executorService) throws JAXBException {
    this.executorService = executorService;
    this.xmlVersionTransformer = new XmlVersionTransformer(this.executorService);
    this.xmlToJsonConverter = new XmlToJsonConverter();
    this.jsonToXmlConverter = new JsonToXmlConverter();
  }

  public VersionTransformer() throws JAXBException {
    this(Executors.newWorkStealingPool());
  }

  /**
   * Method with autodetect EPCIS version from inputStream
   *
   * @param inputDocument EPCIS document in either application/xml or application/json format as a
   *     InputStream
   * @param fromMediaType MediaType of the input EPCIS document, also format to which the output
   *     document will also be converted i.e. application/xml or application/json
   * @return returns the converted EPCIS document as InputStream which can be used for further
   *     processing
   * @throws UnsupportedOperationException if user is trying to convert different version other than
   *     specified then throw the error
   */
  public final InputStream convert(
      final InputStream inputDocument,
      final String fromMediaType,
      final String toMediaType,
      final EpcisVersion toVersion)
      throws UnsupportedOperationException, IOException {
    // pre scan 1024 bytes to detect version
    final byte[] preScan = new byte[1024];
    final int len = inputDocument.read(preScan);
    final String preScanVersion = new String(preScan, StandardCharsets.UTF_8);

    if (!preScanVersion.contains("schemaVersion")) {
      throw new UnsupportedOperationException("unable to detect EPCIS schemaVersion");
    }

    EpcisVersion fromVersion;

    if (preScanVersion.contains("schemaVersion=\"1.2\"")
        || preScanVersion.contains("schemaVersion='1.2'")
        || preScanVersion.replace(" ", "").contains("\"schemaVersion\":\"1.2\"")) {
      fromVersion = EpcisVersion.VERSION_1_2;
    } else if (preScanVersion.contains("schemaVersion=\"2.0\"")
        || preScanVersion.contains("schemaVersion='2.0'")
        || preScanVersion.replace(" ", "").contains("\"schemaVersion\":\"2.0\"")) {
      fromVersion = EpcisVersion.VERSION_2_0;
    } else {
      throw new FormatConverterException(
          "Provided document contains unsupported EPCIS document version");
    }

    final PipedOutputStream pipedOutputStream = new PipedOutputStream();
    final PipedInputStream pipe = new PipedInputStream(pipedOutputStream);
    pipedOutputStream.write(preScan, 0, len);

    executorService.execute(
        () -> {
          try {
            ChannelUtil.copy(inputDocument, pipedOutputStream);
          } catch (Exception e) {
            throw new FormatConverterException(
                "Exception occurred during reading of schema version from input document : "
                    + e.getMessage(),
                e);
          }
        });

    return convert(pipe, fromMediaType, fromVersion, toMediaType, toVersion);
  }

  /**
   * API method to accept EPCIS document input and transform it to corresponding document based on
   * user specification.
   *
   * @param inputDocument EPCIS document in either application/xml or application/json format as a
   *     InputStream
   * @param mediaType MediaType of the input EPCIS document, also format to which the output
   *     document will also be converted i.e. application/xml or application/json
   * @param fromVersion Version of the provided input EPCIS document i.e. 1.2/2.0
   * @param toVersion Version to which provided document need to be converted to 1.2/2.0
   * @return returns the converted EPCIS document as InputStream which can be used for further
   *     processing
   * @throws UnsupportedOperationException if user is trying to convert different version other than
   *     specified then throw the error
   * @throws IOException If any exception occur during the conversion then throw the error
   */
  public final InputStream convert(
      final InputStream inputDocument,
      final String mediaType,
      final EpcisVersion fromVersion,
      final EpcisVersion toVersion)
      throws UnsupportedOperationException, IOException {
    return convert(inputDocument, mediaType, fromVersion, mediaType, toVersion);
  }

  /**
   * API method to accept EPCIS document input and transform it to corresponding document based on
   * user specification.
   *
   * @param inputDocument EPCIS document in either application/xml or application/json format as a
   *     InputStream
   * @param fromMediaType MediaType of the input EPCIS document i.e. application/xml or
   *     application/json
   * @param fromVersion Version of the provided input EPCIS document i.e. 1.2/2.0
   * @param toMediaType MediaType of the converted EPCIS document i.e. application/xml or
   *     application/json
   * @param toVersion Version to which provided document need to be converted to 1.2/2.0
   * @return returns the converted document as InputStream which can be used for further processing
   * @throws UnsupportedOperationException if user is trying to convert different version other than
   *     specified then throw the error
   * @throws IOException If any exception occur during the conversion then throw the error
   */
  public final InputStream convert(
      final InputStream inputDocument,
      final String fromMediaType,
      final EpcisVersion fromVersion,
      final String toMediaType,
      final EpcisVersion toVersion)
      throws UnsupportedOperationException, IOException {
    // If input fromVersion and the required output toVersion is same then return the same input.
    if (fromMediaType.toLowerCase().contains("xml") && toMediaType.toLowerCase().contains("xml")) {
      // If input & conversion mediaType is xml then convert from either 1.2 -> 2.0 or 2.0 -> 1.2
      return xmlVersionTransformer.xmlConverter(inputDocument, fromVersion, toVersion);
    } else if (fromMediaType.toLowerCase().contains("json")
        && toMediaType.toLowerCase().contains("xml")
        && fromVersion.equals(EpcisVersion.VERSION_2_0)
        && toVersion.equals(EpcisVersion.VERSION_2_0)) {
      // If fromMedia is json and toMedia is xml and both versions are 2.0
      return toXml(inputDocument);
    } else if (fromMediaType.toLowerCase().contains("json")
        && toMediaType.toLowerCase().contains("xml")
        && fromVersion.equals(EpcisVersion.VERSION_2_0)
        && toVersion.equals(EpcisVersion.VERSION_1_2)) {
      // If fromMedia is json and toMedia is xml and fromVersion is 2.0 and toVersion is 1.2
      return convert(
          toXml(inputDocument),
          "application/xml",
          EpcisVersion.VERSION_2_0,
          EpcisVersion.VERSION_1_2);
    } else if (fromMediaType.toLowerCase().contains("xml")
        && toMediaType.toLowerCase().contains("json")
        && fromVersion.equals(EpcisVersion.VERSION_2_0)
        && toVersion.equals(EpcisVersion.VERSION_2_0)) {
      // If fromMedia is xml and toMedia is json and both versions are 2.0 convert xml->json
      return toJson(inputDocument);
    } else if (fromMediaType.toLowerCase().contains("xml")
        && toMediaType.toLowerCase().contains("json")
        && fromVersion.equals(EpcisVersion.VERSION_1_2)
        && toVersion.equals(EpcisVersion.VERSION_2_0)) {
      // If fromMedia is xml and toMedia is json and fromVersion is 1.2, toVersion 2.0 then convert
      // xml->2.0 and then to JSON
      final InputStream convertedXml =
          convert(
              inputDocument, "application/xml", EpcisVersion.VERSION_1_2, EpcisVersion.VERSION_2_0);
      return toJson(convertedXml);
    } else {
      throw new UnsupportedOperationException(
          "Requested conversion is not supported, Please check provided MediaType/Version and try again");
    }
  }

  // Private method to convert the JSON 2.0 document -> XML 2.0 and return it as InputStream
  private InputStream toXml(final InputStream inputDocument) {
    try {
      final PipedOutputStream xmlOutputStream = new PipedOutputStream();
      final EventHandler<? extends XmlEPCISEventCollector> handler =
          new EventHandler(new XmlEPCISEventCollector(xmlOutputStream));

      final PipedInputStream convertedDocument = new PipedInputStream(xmlOutputStream);

      executorService.execute(
          () -> {
            try {
              jsonToXmlConverter.convert(inputDocument, handler);
              xmlOutputStream.close();
            } catch (Exception e) {
              try {
                xmlOutputStream.write(e.getMessage().getBytes());
                xmlOutputStream.close();
              } finally {
                throw new FormatConverterException(
                    "Exception occurred during the conversion of JSON 2.0 document to XML 2.0 document  : "
                        + e.getMessage(),
                    e);
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
      final EventHandler<? extends JsonEPCISEventCollector> handler =
          new EventHandler(new JsonEPCISEventCollector(jsonOutputStream));

      final InputStream convertedDocument = new PipedInputStream(jsonOutputStream);

      executorService.execute(
          () -> {
            try {
              xmlToJsonConverter.convert(inputDocument, handler);
            } catch (Exception e) {
              try {
                jsonOutputStream.write(e.getMessage().getBytes());
                jsonOutputStream.close();
              } finally {
                throw new FormatConverterException(
                    "Exception occurred during the conversion of XML 2.0 document to JSON 2.0 document  : "
                        + e.getMessage(),
                    e);
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
}
