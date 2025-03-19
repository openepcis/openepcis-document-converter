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

import static org.junit.jupiter.api.Assertions.*;

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.Conversion;
import io.openepcis.converter.VersionTransformer;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

public class Transform12To20Test {

  private final VersionTransformer versionTransformer;

  public Transform12To20Test() throws JAXBException {
    this.versionTransformer = new VersionTransformer();
  }

  @Test
  void convertDirectTest() throws IOException {
    InputStream inputDocument =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "1.2/EPCIS/XML/Capture/Documents/AggregationEvent_all_possible_fields.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputDocument,
            Conversion.builder()
                .generateGS1CompliantDocument(false)
                .fromMediaType(EPCISFormat.XML)
                .fromVersion(EPCISVersion.VERSION_1_2_0)
                .toVersion(EPCISVersion.VERSION_2_0_0)
                .build());
    assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  @Test
  void convertWithScanTest() throws IOException {
    InputStream inputDocument =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "1.2/EPCIS/XML/Capture/Documents/TransformationEvent_all_possible_fields.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputDocument,
            Conversion.builder()
                .generateGS1CompliantDocument(false)
                .fromMediaType(EPCISFormat.XML)
                .toMediaType(EPCISFormat.XML)
                .toVersion(EPCISVersion.VERSION_2_0_0)
                .build());
    assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  @Test
  void convertWithAllInfoTest() throws IOException {
    InputStream inputDocument =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "1.2/EPCIS/XML/Capture/Documents/All_eventTypes_in_single_document.xml");

    var conversion =
        Conversion.builder()
            .generateGS1CompliantDocument(false)
            .fromMediaType(EPCISFormat.XML)
            .fromVersion(EPCISVersion.VERSION_1_2_0)
            .toMediaType(EPCISFormat.XML)
            .toVersion(EPCISVersion.VERSION_2_0_0)
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
