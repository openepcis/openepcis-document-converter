package io.openepcis.convert.version;

import io.openepcis.convert.EPCISVersion;
import io.openepcis.convert.exception.FormatConverterException;
import io.smallrye.mutiny.Uni;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public class XmlVersionTransformer {

  private static final TransformerFactory TRANSFORMER_FACTORY = TransformerFactory.newInstance();
  private static final Transformer FROM_1_2_TO_2_0;
  private static final Transformer FROM_2_0_TO_1_2;

  private ExecutorService executorService;

  public XmlVersionTransformer() {
    this.executorService = Executors.newWorkStealingPool();
  }

  public XmlVersionTransformer(final ExecutorService executorService) {
    this.executorService = executorService;
  }

  static {
    try {
      FROM_1_2_TO_2_0 =
          TRANSFORMER_FACTORY.newTransformer(
              new StreamSource(
                  XmlVersionTransformer.class
                      .getClassLoader()
                      .getResourceAsStream("xalan-conversion/convert-1.2-to-2.0.xsl")));

      FROM_2_0_TO_1_2 =
          TRANSFORMER_FACTORY.newTransformer(
              new StreamSource(
                  XmlVersionTransformer.class
                      .getClassLoader()
                      .getResourceAsStream("xalan-conversion/convert-2.0-to-1.2.xsl")));
    } catch (TransformerConfigurationException e) {
      throw new FormatConverterException(
          "Creation of Transformer instance failed : " + e.getMessage());
    }
  }

  public static final InputStream convert(
      final InputStream inputStream, final EPCISVersion from, final EPCISVersion to)
      throws UnsupportedOperationException {
    if (from.equals(to)) {
      return inputStream;
    }
    return inputStream;
  }

  /**
   * Convert EPCIS 1.2 XML document to EPCIS 2.0 XML document
   *
   * @param lowerVersionDocument EPCIS 1.2 document as a InputStream
   * @return converted EPCIS 2.0 document as a StreamingOutput
   */
  public static Uni<StreamingOutput> fromLower(final InputStream lowerVersionDocument) {
    return Uni.createFrom()
        .item(
            new StreamingOutput() {
              @Override
              public void write(OutputStream outputStream) throws WebApplicationException {
                final StreamSource inputDocument = new StreamSource(lowerVersionDocument);
                try {
                  FROM_1_2_TO_2_0.transform(inputDocument, new StreamResult(outputStream));
                } catch (TransformerException e) {
                  throw new FormatConverterException(
                      "Exception occurred during conversion of XML from EPCIS 1.2 to 2.0 : "
                          + e.getMessage());
                }
              }
            });
  }

  /**
   * Convert EPCIS 2.0 XML document to EPCIS 1.2 XML document
   *
   * @param higherVersionDocument EPCIS 2.0 document as a InputStream
   * @return converted EPCIS 1.2 document as a StreamingOutput
   */
  public static Uni<StreamingOutput> fromHigher(final InputStream higherVersionDocument) {
    return Uni.createFrom()
        .item(
            outputStream -> {
              try {
                FROM_2_0_TO_1_2.transform(
                    new StreamSource(higherVersionDocument), new StreamResult(outputStream));
              } catch (TransformerException e) {
                throw new FormatConverterException(
                    "Exception occurred during conversion of XML from EPCIS 2.0 to 1.2 : "
                        + e.getMessage());
              }
            });
  }
}
