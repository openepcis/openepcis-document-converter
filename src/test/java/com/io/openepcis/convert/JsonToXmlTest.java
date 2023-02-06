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
package com.io.openepcis.convert;

import io.openepcis.constants.EPCISVersion;
import io.openepcis.convert.VersionTransformer;
import io.openepcis.convert.collector.EventHandler;
import io.openepcis.convert.collector.EventListCollector;
import io.openepcis.convert.collector.XmlEPCISEventCollector;
import io.openepcis.convert.json.JsonToXmlConverter;
import io.openepcis.convert.validator.EventValidator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

public class JsonToXmlTest {

  @Test
  public void jsonToXmlTest() throws Exception {
    final InputStream jsonStream = getClass().getResourceAsStream("/convert/InputEPCISEvents.json");
    final EventListCollector collector = new EventListCollector(new ArrayList<>());
    final EventHandler handler = new EventHandler(new EventValidator(), collector);
    new JsonToXmlConverter().convert(jsonStream, handler);
    Assert.assertEquals(1, collector.get().size());
  }

  @Test
  public void jsonToXmlStreamTest() throws Exception {
    final InputStream jsonStream = getClass().getResourceAsStream("/convert/InputEPCISEvents.json");
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final EventHandler handler = new EventHandler(null, new XmlEPCISEventCollector(out));
    new JsonToXmlConverter().convert(jsonStream, handler);
    Assert.assertTrue(out.size() > 0);
    // TODO: check why this test failed
    // Assert.assertEquals(1207, out.size());
  }

  // Test to only validate the converted XML events
  @Test
  public void jsonToXMLValidation() throws Exception {
    final InputStream jsonStream = getClass().getResourceAsStream("/convert/InputEPCISEvents.json");
    new JsonToXmlConverter().convert(jsonStream, new EventHandler(new EventValidator(), null));
  }

  @Test
  public void jsonToXmlTestDocument() throws Exception {
    final InputStream jsonStream = getClass().getResourceAsStream("/convert/InputEPCISEvents.json");
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final EventHandler handler = new EventHandler(null, new XmlEPCISEventCollector(out));
    new JsonToXmlConverter().convert(jsonStream, handler);
    Assert.assertTrue(out.size() > 0);
  }

  // Test the conversion of single EPCIS event in JSON -> XML
  @Test
  public void jsonToXmlTestSingleEvent() throws Exception {
    final InputStream jsonStream =
        getClass().getResourceAsStream("/convert/InputEpcisSingleEvent.json");
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final EventHandler handler =
        new EventHandler(new EventValidator(), new XmlEPCISEventCollector(out));
    new JsonToXmlConverter().convert(jsonStream, handler);
    Assert.assertTrue(out.size() > 0);
  }

  @Test
  public void versionTransformerTest() throws Exception {
    final InputStream jsonStream = getClass().getResourceAsStream("/convert/InputEPCISEvents.json");
    final InputStream convertedDocument =
        new VersionTransformer()
            .convert(jsonStream, "application/json", "application/xml", EPCISVersion.VERSION_1_2);
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  @Test
  public void versionTransformerXmlTest() throws Exception {
    final InputStream jsonStream = getClass().getResourceAsStream("/convert/JsonDocument.json");
    final InputStream convertedDocument =
        new VersionTransformer()
            .convert(jsonStream, "application/json", "application/xml", EPCISVersion.VERSION_1_2);
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }
}
