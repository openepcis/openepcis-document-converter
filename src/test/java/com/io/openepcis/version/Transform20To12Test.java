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

public class Transform20To12Test {

  private VersionTransformer versionTransformer;
  private InputStream inputDocument;

  @Before
  public void before() throws Exception {
    versionTransformer = new VersionTransformer();
  }

  @Test
  public void convertDirectTest() throws IOException {
    inputDocument =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/XML/Capture/Documents/ObjectEvent_all_possible_fields.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputDocument,
            "application/xml",
            EPCISVersion.VERSION_2_0_0,
            EPCISVersion.VERSION_1_2_0);
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  @Test
  public void convertWithScanTest() throws IOException {
    inputDocument =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/XML/Capture/Documents/AssociationEvent_all_possible_fields.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputDocument, "application/xml", "application/xml", EPCISVersion.VERSION_1_2_0);
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  @Test
  public void convertWithAllInfoTest() throws IOException {
    inputDocument =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/XML/Capture/Documents/Combination_of_different_event.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputDocument,
            "xml",
            EPCISVersion.VERSION_2_0_0,
            "application/xml",
            EPCISVersion.VERSION_1_2_0);
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }
}
