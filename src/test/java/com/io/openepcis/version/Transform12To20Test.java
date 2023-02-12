/*
 * Copyright 2022 benelog GmbH & Co. KG
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
            inputDocument, EPCISFormat.XML, EPCISVersion.VERSION_1_2_0, EPCISVersion.VERSION_2_0_0);
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
            inputDocument, EPCISFormat.XML, EPCISFormat.XML, EPCISVersion.VERSION_2_0_0);
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
            EPCISFormat.XML,
            EPCISVersion.VERSION_1_2_0,
            EPCISFormat.XML,
            EPCISVersion.VERSION_2_0_0);
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }
}
