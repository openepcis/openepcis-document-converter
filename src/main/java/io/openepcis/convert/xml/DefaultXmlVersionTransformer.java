/*
 * Copyright 2022 benelog GmbH & Co. KG
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
package io.openepcis.convert.xml;

import io.openepcis.constants.EPCISVersion;
import io.openepcis.convert.exception.FormatConverterException;
import java.io.*;
import java.util.concurrent.ExecutorService;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * Class for handling the conversion of EPCIS 1.2 document in XML format to EPCIS 2.0 XML document
 * and vice versa. This is Non-thread safe XML converter for EPCIS events. Do not share an instance
 * across threads. EventsConverter: Public method that will be called by client during the
 * conversions.
 */
public class DefaultXmlVersionTransformer implements XmlVersionTransformer {

  private final Transformer from12To20;
  private final Transformer from20T012;
  private final ExecutorService executorService;

  public DefaultXmlVersionTransformer(final ExecutorService executorService) {
    this.executorService = executorService;
    try {
      TransformerFactory transformerFactory = TransformerFactory.newInstance();

      from12To20 =
          transformerFactory.newTransformer(
              new StreamSource(
                  DefaultXmlVersionTransformer.class
                      .getClassLoader()
                      .getResourceAsStream("xalan-conversion/convert-1.2-to-2.0.xsl")));

      from20T012 =
          transformerFactory.newTransformer(
              new StreamSource(
                  DefaultXmlVersionTransformer.class
                      .getClassLoader()
                      .getResourceAsStream("xalan-conversion/convert-2.0-to-1.2.xsl")));
    } catch (TransformerConfigurationException e) {
      throw new FormatConverterException(
          "Creation of Transformer instance failed : " + e.getMessage());
    }
  }

  /**
   * Public method invoked by the calling application by indicating the type of conversion i.e. from
   * XML 1.2 -> XML 2.0 or vice versa.
   *
   * @param inputStream Stream of EPCIS 1.2/2.0 XML document.
   * @param fromVersion Indicating the version of the provided input EPCIS XML document.
   * @param toVersion Indicating the version to which provided input EPCIS XML document needs to be
   *     converted to.
   * @return returns the InputStream of EPCIS 1.2/2.0 XML document.
   * @throws UnsupportedOperationException if user is trying to convert different version other than
   *     specified then throw the error
   * @throws IOException If any exception occur during the conversion then throw the error
   */
  @Override
  public final InputStream xmlConverter(
      final InputStream inputStream, final EPCISVersion fromVersion, final EPCISVersion toVersion)
      throws UnsupportedOperationException, IOException {
    if (fromVersion.equals(toVersion)) {
      // if input document version and conversion version are equal then return same document.
      return inputStream;
    } else if (fromVersion.equals(EPCISVersion.VERSION_1_2_0)
        && toVersion.equals(EPCISVersion.VERSION_2_0_0)) {
      // If input document version is 1.2 and conversion version is 2.0, convert from XML 1.2 -> 2.0
      return convert12To20(inputStream);
    } else if (fromVersion.equals(EPCISVersion.VERSION_2_0_0)
        && toVersion.equals(EPCISVersion.VERSION_1_2_0)) {
      // If input document version is 2.0 and conversion version is 1.2, convert from XML 2.0 -> 1.2
      return convert20To12(inputStream);
    } else {
      throw new UnsupportedOperationException(
          "Requested conversion is not supported, Please check provided MediaType/Version and try again");
    }
  }

  /**
   * Convert EPCIS 1.2 XML document to EPCIS 2.0 XML document
   *
   * @param inputDocument EPCIS 1.2 XML document as a InputStream
   * @return converted EPCIS 2.0 XML document as a InputStream
   * @throws IOException If any exception occur during the conversion then throw the error
   */
  private InputStream convert12To20(final InputStream inputDocument) throws IOException {
    final PipedOutputStream outTransform = new PipedOutputStream();
    final InputStream convertedDocument = new PipedInputStream(outTransform);

    executorService.execute(
        () -> {
          try {
            from12To20.transform(
                new StreamSource(inputDocument),
                new StreamResult(new BufferedOutputStream(outTransform)));
            outTransform.close();
          } catch (Exception e) {
            try {
              outTransform.write(e.getMessage().getBytes());
              outTransform.close();
            } finally {
              throw new FormatConverterException(
                  "Exception occurred during conversion of EPCIS XML document from 1.2 to 2.0 : "
                      + e.getMessage(),
                  e);
            }
          }
        });
    return convertedDocument;
  }

  /**
   * Convert EPCIS 2.0 XML document to EPCIS 1.2 XML document
   *
   * @param inputDocument EPCIS 2.0 document as a InputStream
   * @return converted EPCIS 1.2 XML document as a InputStream
   * @throws IOException If any exception occur during the conversion then throw the error
   */
  private InputStream convert20To12(final InputStream inputDocument) throws IOException {
    final PipedOutputStream outTransform = new PipedOutputStream();
    final InputStream convertedDocument = new PipedInputStream(outTransform);

    executorService.execute(
        () -> {
          try {
            from20T012.transform(
                new StreamSource(inputDocument),
                new StreamResult(new BufferedOutputStream(outTransform)));
            outTransform.close();
          } catch (Exception e) {
            try {
              outTransform.write(e.getMessage().getBytes());
              outTransform.close();
            } finally {
              throw new FormatConverterException(
                  "Exception occurred during conversion of EPCIS XML document from 2.0 to 1.2, Failed to convert : "
                      + e.getMessage(),
                  e);
            }
          }
        });
    return convertedDocument;
  }
}
