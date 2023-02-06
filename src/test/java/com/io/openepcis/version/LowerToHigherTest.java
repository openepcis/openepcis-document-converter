package com.io.openepcis.version;

import io.openepcis.constants.EPCISVersion;
import io.openepcis.convert.VersionTransformer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LowerToHigherTest {

  private VersionTransformer versionTransformer;
  private InputStream inputDocument;

  @Before
  public void before() throws Exception {
    versionTransformer = new VersionTransformer();
  }

  @Test
  public void convertDirectTest() throws IOException {
    inputDocument = getClass().getResourceAsStream("/version/LowerVersionXml_1.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputDocument, "xml", EPCISVersion.VERSION_1_2, EPCISVersion.VERSION_2_0);
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  @Test
  public void convertWithScanTest() throws IOException {
    inputDocument = getClass().getResourceAsStream("/version/LowerVersionXml_2.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputDocument, "application/xml", "application/xml", EPCISVersion.VERSION_2_0);
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  @Test
  public void convertWithAllInfoTest() throws IOException {
    inputDocument = getClass().getResourceAsStream("/version/LowerVersionXml_3.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputDocument,
            "application/xml",
            EPCISVersion.VERSION_1_2,
            "xml",
            EPCISVersion.VERSION_2_0);
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }
}
