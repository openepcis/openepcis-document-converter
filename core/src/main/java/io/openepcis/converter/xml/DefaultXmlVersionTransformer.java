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
package io.openepcis.converter.xml;

import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.Conversion;
import io.openepcis.converter.VersionTransformerFeature;
import io.openepcis.converter.exception.FormatConverterException;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Class for handling the conversion of EPCIS 1.2 document in XML format to EPCIS 2.0 XML document
 * and vice versa. This is Non-thread safe XML converter for EPCIS events. Do not share an instance
 * across threads. EventsConverter: Public method that will be called by client during the
 * conversions.
 */
public class DefaultXmlVersionTransformer implements XmlVersionTransformer {

  private final ExecutorService executorService;

  private static final TransformerFactory TRANSFORMER_FACTORY = createTransformerFactory();

  private static Templates FROM_12_TO_20;

  private static Templates FROM_20_TO_12;

  private static TransformerFactory createTransformerFactory() {

    final TransformerFactory transformerFactory = TransformerFactory.newInstance();
    try {
      FROM_12_TO_20 = transformerFactory.newTemplates(
              new StreamSource(DefaultXmlVersionTransformer.class
                      .getClassLoader()
                      .getResourceAsStream("xalan-conversion/convert-1.2-to-2.0.xsl")));
      FROM_20_TO_12 = transformerFactory.newTemplates(
              new StreamSource(DefaultXmlVersionTransformer.class.getClassLoader()
                      .getResourceAsStream("xalan-conversion/convert-2.0-to-1.2.xsl")));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return transformerFactory;
  }

  public DefaultXmlVersionTransformer(final ExecutorService executorService) {
    this.executorService = executorService;
  }


    @Override
    public InputStream xmlConverter(InputStream inputStream, Function<Conversion.StartStage, Conversion.BuildStage> fn) throws UnsupportedOperationException, IOException {
      return xmlConverter(inputStream, fn.apply(Conversion.builder()).build());
    }

    /**
     * Public method invoked by the calling application by indicating the type of conversion i.e. from
     * XML 1.2 -> XML 2.0 or vice versa.
     *
     * @param inputStream Stream of EPCIS 1.2/2.0 XML document.
     * @param conversion  Conversion setting
     * @return returns the InputStream of EPCIS 1.2/2.0 XML document.
     * @throws UnsupportedOperationException if user is trying to convert different version other than
     *                                       specified then throw the error
     * @throws IOException                   If any exception occur during the conversion then throw the error
     */
    @Override
    public final InputStream xmlConverter(
            final InputStream inputStream, final Conversion conversion)
            throws UnsupportedOperationException, IOException {
      if (conversion.fromVersion().equals(conversion.toVersion())) {
        // if input document version and conversion version are equal then return same document.
        return inputStream;
      } else if (EPCISVersion.VERSION_1_2_0.equals(conversion.fromVersion())
              && EPCISVersion.VERSION_2_0_0.equals(conversion.toVersion())) {
        // If input document version is 1.2 and conversion version is 2.0, convert from XML 1.2 -> 2.0
        return convert12To20(inputStream);
      } else if (EPCISVersion.VERSION_2_0_0.equals(conversion.fromVersion())
              && EPCISVersion.VERSION_1_2_0.equals(conversion.toVersion())) {
        // If input document version is 2.0 and conversion version is 1.2, convert from XML 2.0 -> 1.2
        try {
          return convert20To12(inputStream, VersionTransformerFeature.enabledFeatures(conversion));
        } catch (TransformerConfigurationException e) {
          throw new UnsupportedOperationException(e.getMessage(), e);
        }
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
    private InputStream convert12To20(final InputStream inputDocument) {
      final PipedInputStream convertedDocument = new PipedInputStream();
      final AtomicBoolean pipeConnected = new AtomicBoolean(false);

      executorService.execute(
              () -> {
                final PipedOutputStream outTransform = new PipedOutputStream();
                try (outTransform) {
                  outTransform.connect(convertedDocument);
                  pipeConnected.set(true);
                  FROM_12_TO_20.newTransformer().transform(
                          new StreamSource(inputDocument),
                          new StreamResult(new BufferedOutputStream(outTransform)));
                } catch (Exception e) {
                  try {
                    outTransform.write(e.getMessage().getBytes());
                  } catch (IOException ioException) {
                    // ignore
                  }
                  throw new FormatConverterException(
                          "Exception occurred during conversion of EPCIS XML document from 1.2 to 2.0 : "
                                  + e.getMessage(),
                          e);

                }
              });
      while (!pipeConnected.get()) {
        Thread.yield();
      }
      return convertedDocument;
    }

    /**
     * Convert EPCIS 2.0 XML document to EPCIS 1.2 XML document
     *
     * @param inputDocument   EPCIS 2.0 document as a InputStream
     * @param enabledFeatures list of enabled VersionTransformer features
     * @return converted EPCIS 1.2 XML document as a InputStream
     * @throws IOException If any exception occur during the conversion then throw the error
     */
    private InputStream convert20To12(final InputStream inputDocument, final List<VersionTransformerFeature> enabledFeatures) throws TransformerConfigurationException {
      final PipedInputStream convertedDocument = new PipedInputStream();
      final AtomicBoolean pipeConnected = new AtomicBoolean(false);
      final Transformer from20T012 = FROM_20_TO_12.newTransformer();
      from20T012.setParameter("includeAssociationEvent", enabledFeatures.contains(VersionTransformerFeature.EPCIS_1_2_0_INCLUDE_ASSOCIATION_EVENT) ? "yes" : "no");
      from20T012.setParameter("includePersistentDisposition", enabledFeatures.contains(VersionTransformerFeature.EPCIS_1_2_0_INCLUDE_PERSISTENT_DISPOSITION) ? "yes" : "no");
      from20T012.setParameter("includeSensorElementList", enabledFeatures.contains(VersionTransformerFeature.EPCIS_1_2_0_INCLUDE_SENSOR_ELEMENT_LIST) ? "yes" : "no");

      executorService.execute(
              () -> {
                final PipedOutputStream outTransform = new PipedOutputStream();
                try (outTransform) {
                  outTransform.connect(convertedDocument);
                  pipeConnected.set(true);
                  from20T012.transform(
                          new StreamSource(inputDocument),
                          new StreamResult(new BufferedOutputStream(outTransform)));
                } catch (Exception e) {
                  try {
                    outTransform.write(e.getMessage().getBytes());
                  } catch (IOException ioException) {
                    // ignore
                  }
                  throw new FormatConverterException(
                          "Exception occurred during conversion of EPCIS XML document from 2.0 to 1.2, Failed to convert : "
                                  + e.getMessage(),
                          e);
                }
              });
      while (!pipeConnected.get()) {
        Thread.yield();
      }
      return convertedDocument;
    }

}
