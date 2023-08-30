/*
 * Copyright 2022-2023 benelog GmbH & Co. KG
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

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.convert.Conversion;
import io.openepcis.convert.VersionTransformer;
import jakarta.xml.bind.JAXBException;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class Transform20To12Test {

  private final VersionTransformer versionTransformer;
  public Transform20To12Test() throws JAXBException {
    versionTransformer = new VersionTransformer();
  }


  @Test
  public void convertDirectTest() throws IOException {
    InputStream inputDocument =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/XML/Capture/Documents/ObjectEvent_all_possible_fields.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputDocument,
            Conversion.builder()
                .generateGS1CompliantDocument(false)
                .fromMediaType(EPCISFormat.XML)
                .fromVersion(EPCISVersion.VERSION_2_0_0)
                .toVersion(EPCISVersion.VERSION_1_2_0)
                .build());
    assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  @Test
  public void convertWithScanTest() throws IOException {
    InputStream inputDocument =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/XML/Capture/Documents/AssociationEvent_all_possible_fields.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputDocument,
            Conversion.builder()
                .generateGS1CompliantDocument(false)
                .fromMediaType(EPCISFormat.XML)
                .toMediaType(EPCISFormat.XML)
                .toVersion(EPCISVersion.VERSION_1_2_0)
                .build());
    assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  @Test
  public void convertWithAllInfoTest() throws IOException {
    InputStream inputDocument =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/XML/Capture/Documents/Combination_of_different_event.xml");

    var conversion = Conversion.builder()
        .generateGS1CompliantDocument(false)
        .fromMediaType(EPCISFormat.XML)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_1_2_0)
        .build();

    final InputStream convertedDocument = versionTransformer.convert(inputDocument, conversion);
    assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }
}
