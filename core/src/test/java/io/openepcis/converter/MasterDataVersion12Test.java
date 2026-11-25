/*
 * Copyright 2022-2026 benelog GmbH & Co. KG
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
package io.openepcis.converter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The blocking {@link VersionTransformer} is what a REST service calls to turn its marshalled 2.0
 * document into EPCIS 1.2. An event extension in the well-known gs1 namespace must come out
 * well-formed and complete.
 */
class MasterDataVersion12Test {

  @Test
  @DisplayName("gs1:masterDataAvailableFor survives XML 2.0 to XML 1.2")
  void masterDataSurvivesXml20ToXml12() throws Exception {
    final String xml;
    try (InputStream in = getClass().getResourceAsStream("/XML/masterDataAvailableForExtension.xml")) {
      assertNotNull(in);
      try (InputStream converted =
          new VersionTransformer()
              .convert(
                  in,
                  Conversion.of(
                      EPCISFormat.XML,
                      EPCISVersion.VERSION_2_0_0,
                      EPCISFormat.XML,
                      EPCISVersion.VERSION_1_2_0))) {
        xml = new String(converted.readAllBytes(), StandardCharsets.UTF_8);
      }
    }

    assertTrue(xml.contains("urn:epcglobal:epcis:xsd:1"), xml);
    MasterDataAssertions.assertMasterDataKept(
        xml, "urn:epc:id:sgtin:9521234.000001.BAT2024-001");
  }
}
