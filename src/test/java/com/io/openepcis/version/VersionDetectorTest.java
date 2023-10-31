package com.io.openepcis.version;

import io.openepcis.constants.EPCISVersion;
import io.openepcis.convert.VersionTransformer;
import io.openepcis.resources.util.Commons;
import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.IOException;

public class VersionDetectorTest {
  private final VersionTransformer versionTransformer;

  public VersionDetectorTest() throws JAXBException {
    versionTransformer = new VersionTransformer();
  }

  @Test
  void testJSON_2_0_0() throws IOException {
    final EPCISVersion version = versionTransformer.versionDetector(new BufferedInputStream(Commons.getInputStream("2.0/EPCIS/JSON/Capture/Documents/Combination_of_different_event.json")));
    Assertions.assertEquals(EPCISVersion.VERSION_2_0_0, version);
  }

  @Test
  void testXML_2_0_0() throws IOException {
    final EPCISVersion version = versionTransformer.versionDetector(new BufferedInputStream(Commons.getInputStream("2.0/EPCIS/XML/Capture/Documents/Combination_of_different_event.xml")));
    Assertions.assertEquals(EPCISVersion.VERSION_2_0_0, version);
  }

  @Test
  void testXML_1_2_0() throws IOException {
    final EPCISVersion version = versionTransformer.versionDetector(new BufferedInputStream(Commons.getInputStream("1.2/EPCIS/XML/Capture/Documents/ObjectEvent.xml")));
    Assertions.assertEquals(EPCISVersion.VERSION_1_2_0, version);
  }
  // new String(Commons.getInputStream("2.0/EPCIS/JSON/Capture/Documents/Combination_of_different_event.json").readAllBytes(), StandardCharsets.UTF_8);
}
