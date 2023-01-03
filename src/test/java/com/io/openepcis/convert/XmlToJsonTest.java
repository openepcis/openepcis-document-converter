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

import io.openepcis.convert.EpcisVersion;
import io.openepcis.convert.VersionTransformer;
import io.openepcis.convert.collector.EventHandler;
import io.openepcis.convert.collector.EventListCollector;
import io.openepcis.convert.collector.JsonEPCISEventCollector;
import io.openepcis.convert.exception.FormatConverterException;
import io.openepcis.convert.validator.EventValidator;
import io.openepcis.convert.xml.XmlToJsonConverter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

public class XmlToJsonTest {

  // Test case for Valid XML to JSON-LD conversion
  @Test
  public void xmlToJsonTest() throws Exception {
    final InputStream xmlStream = getClass().getResourceAsStream("/convert/InputEPCISEvents.xml");
    final EventListCollector collector = new EventListCollector(new ArrayList<>());
    final EventHandler handler = new EventHandler(new EventValidator(), collector);
    new XmlToJsonConverter().convert(xmlStream, handler);

    // Check for the eventList size
    Assert.assertEquals(2, collector.get().size());
  }

  // Test case for Invalid values for EventHandler
  @Test(expected = FormatConverterException.class)
  public void xmlToJsonInvalidTest() throws Exception {
    final InputStream xmlStream = getClass().getResourceAsStream("/convert/InputEPCISEvents.xml");
    final EventHandler handler = new EventHandler(null, null);
    new XmlToJsonConverter().convert(xmlStream, handler);
  }

  // Test case for Invalid JSON file contents
  @Test(expected = FormatConverterException.class)
  public void xmlToJsonInvalidJSONContent() throws Exception {
    final InputStream xmlStream = getClass().getResourceAsStream("/InvalidJSONContent.xml");
    final EventHandler handler = new EventHandler(new EventValidator(), null);
    new XmlToJsonConverter().convert(xmlStream, handler);
  }

  // Test to only validate the converted JSON events against JSON-Schema
  @Test
  public void xmlToJsonValidate() throws Exception {
    final InputStream xmlStream = getClass().getResourceAsStream("/convert/InputEPCISEvents.xml");
    new XmlToJsonConverter().convert(xmlStream, new EventHandler(new EventValidator(), null));
  }

  @Test
  public void xmlToJsonStreamTest() throws Exception {
    final InputStream xmlStream = getClass().getResourceAsStream("/convert/InputEPCISEvents.xml");
    final ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    final JsonEPCISEventCollector collector = new JsonEPCISEventCollector(jsonOutput);
    final EventHandler handler = new EventHandler(new EventValidator(), collector);
    new XmlToJsonConverter().convert(xmlStream, handler);
    Assert.assertTrue(jsonOutput.size() > 0);
    Assert.assertTrue(jsonOutput.toString().contains("eventList"));
  }

  // Test the single event
  @Test
  public void xmlToJsonSingleEvent() throws Exception {
    final InputStream xmlStream =
        getClass().getResourceAsStream("/convert/InputEpcisSingleEvent.xml");
    final ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    final JsonEPCISEventCollector collector = new JsonEPCISEventCollector(jsonOutput);
    final EventHandler handler = new EventHandler(new EventValidator(), collector);
    new XmlToJsonConverter().convert(xmlStream, handler);
    Assert.assertTrue(jsonOutput.size() > 0);
  }

  @Test
  public void jsonConversionTest() throws Exception {
    final InputStream xmlStream = getClass().getResourceAsStream("/convert/XmlDocument.xml");
    final InputStream convertedDocument =
        new VersionTransformer()
            .convert(
                xmlStream,
                "application/xml",
                EpcisVersion.VERSION_2_0,
                "application/json",
                EpcisVersion.VERSION_2_0);
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  @Test
  public void jsonConversionScanTest() throws Exception {
    final InputStream xmlDocument = getClass().getResourceAsStream("/convert/XmlDocument.xml");
    final VersionTransformer versionTransformer = new VersionTransformer();
    final InputStream convertedDocument =
        versionTransformer.convert(
            xmlDocument, "application/xml", "application/json", EpcisVersion.VERSION_2_0);
    ;
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }
}
