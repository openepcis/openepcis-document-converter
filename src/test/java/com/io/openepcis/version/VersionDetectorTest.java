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
