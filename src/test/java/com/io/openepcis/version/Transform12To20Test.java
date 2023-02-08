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

public class Transform12To20Test {

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
                "1.2/EPCIS/XML/Capture/Documents/AggregationEvent_all_possible_fields.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputDocument, "xml", EPCISVersion.VERSION_1_2_0, EPCISVersion.VERSION_2_0_0);
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
                "1.2/EPCIS/XML/Capture/Documents/TransformationEvent_all_possible_fields.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputDocument, "application/xml", "application/xml", EPCISVersion.VERSION_2_0_0);
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
                "1.2/EPCIS/XML/Capture/Documents/All_eventTypes_in_single_document.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputDocument,
            "application/xml",
            EPCISVersion.VERSION_1_2_0,
            "xml",
            EPCISVersion.VERSION_2_0_0);
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }
}
