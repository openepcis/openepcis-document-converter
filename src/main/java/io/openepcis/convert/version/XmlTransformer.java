package io.openepcis.convert.version;

import io.openepcis.convert.exception.FormatConverterException;
import io.smallrye.mutiny.Uni;
import java.io.InputStream;
import java.io.OutputStream;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class XmlTransformer {

  private static final TransformerFactory transformerFactory = TransformerFactory.newInstance();
  private static final Transformer toHigherTransformer;
  private static final Transformer toLowerTransformer;

  static {
    try {
      toHigherTransformer =
          transformerFactory.newTransformer(
              new StreamSource(
                  XmlTransformer.class
                      .getClassLoader()
                      .getResourceAsStream("xalan-conversion/convert-1.2-to-2.0.xsl")));

      toLowerTransformer =
          transformerFactory.newTransformer(
              new StreamSource(
                  XmlTransformer.class
                      .getClassLoader()
                      .getResourceAsStream("xalan-conversion/convert-2.0-to-1.2.xsl")));
    } catch (TransformerConfigurationException e) {
      throw new FormatConverterException(
          "Creation of Transformer instance failed : " + e.getMessage());
    }
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
                  toHigherTransformer.transform(inputDocument, new StreamResult(outputStream));
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
                toLowerTransformer.transform(
                    new StreamSource(higherVersionDocument), new StreamResult(outputStream));
              } catch (TransformerException e) {
                throw new FormatConverterException(
                    "Exception occurred during conversion of XML from EPCIS 2.0 to 1.2 : "
                        + e.getMessage());
              }
            });
  }
}
