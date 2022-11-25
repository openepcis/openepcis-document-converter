package com.io.openepcis.version;

import io.openepcis.convert.EpcisVersion;
import io.openepcis.convert.VersionTransformer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

public class LowerToHigherTest {

  private VersionTransformer versionTransformer;
  private InputStream inputDocument;

  @Before
  public void before() {
    versionTransformer = new VersionTransformer();
  }

  @Test
  public void convertDirectTest() throws IOException {
    inputDocument = getClass().getResourceAsStream("/version/LowerVersionXml_1.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputDocument, "xml", EpcisVersion.VERSION_1_2, EpcisVersion.VERSION_2_0);
    System.out.println(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8));
  }

  @Test
  public void convertWithScanTest() throws IOException {
    inputDocument = getClass().getResourceAsStream("/version/LowerVersionXml_2.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputDocument, "application/xml", "application/xml", EpcisVersion.VERSION_2_0);
    System.out.println(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8));
  }

  @Test
  public void convertWithAllInfoTest() throws IOException {
    inputDocument = getClass().getResourceAsStream("/version/LowerVersionXml_3.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputDocument,
            "application/xml",
            EpcisVersion.VERSION_1_2,
            "xml",
            EpcisVersion.VERSION_2_0);
    System.out.println(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8));
  }
}
