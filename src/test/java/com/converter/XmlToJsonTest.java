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
package com.converter;

import io.openepcis.epc.formatconverter.XmlToJsonConverter;
import io.openepcis.epc.formatconverter.customizer.EventFormatConversionException;
import io.openepcis.epc.formatconverter.outputhandler.EventHandler;
import io.openepcis.epc.formatconverter.outputhandler.EventJSONStreamCollector;
import io.openepcis.epc.formatconverter.outputhandler.EventListCollector;
import io.openepcis.epc.formatconverter.outputhandler.EventValidator;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import org.junit.Assert;
import org.junit.Test;

public class XmlToJsonTest {

  // Test case for Valid XML to JSON-LD conversion
  @Test
  public void xmlToJsonTest() throws Exception {
    final InputStream xmlStream = getClass().getResourceAsStream("/InputEPCISEvents.xml");
    final EventListCollector collector = new EventListCollector(new ArrayList<>());
    final EventHandler handler = new EventHandler(new EventValidator(), collector);
    new XmlToJsonConverter().convert(xmlStream, handler);

    // Check for the eventList size
    Assert.assertEquals(2, collector.get().size());
  }

  // Test case for Invalid values for EventHandler
  @Test(expected = EventFormatConversionException.class)
  public void xmlToJsonInvalidTest() throws Exception {
    final InputStream xmlStream = getClass().getResourceAsStream("/InputEPCISEvents.xml");
    final EventHandler handler = new EventHandler(null, null);
    new XmlToJsonConverter().convert(xmlStream, handler);
  }

  // Test case for Invalid JSON file contents
  @Test(expected = EventFormatConversionException.class)
  public void xmlToJsonInvalidJSONContent() throws Exception {
    final InputStream xmlStream = getClass().getResourceAsStream("/InvalidJSONContent.xml");
    final EventHandler handler = new EventHandler(new EventValidator(), null);
    new XmlToJsonConverter().convert(xmlStream, handler);
  }

  // Test to only validate the converted JSON events against JSON-Schema
  @Test
  public void xmlToJsonValidate() throws Exception {
    final InputStream xmlStream = getClass().getResourceAsStream("/InputEPCISEvents.xml");
    new XmlToJsonConverter().convert(xmlStream, new EventHandler(new EventValidator(), null));
  }

  @Test
  public void xmlToJsonStreamTest() throws Exception {
    final InputStream xmlStream = getClass().getResourceAsStream("/InputEPCISEvents.xml");
    final ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    final EventJSONStreamCollector collector = new EventJSONStreamCollector(jsonOutput);
    final EventHandler handler = new EventHandler(new EventValidator(), collector);
    new XmlToJsonConverter().convert(xmlStream, handler);
    Assert.assertTrue(jsonOutput.size() > 0);
    Assert.assertTrue(jsonOutput.toString().contains("eventList"));
    System.out.println(jsonOutput.toString());
  }

  // Test the single event
  @Test
  public void xmlToJsonSingleEvent() throws Exception {
    final InputStream xmlStream = getClass().getResourceAsStream("/InputEpcisSingleEvent.xml");
    final ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    final EventJSONStreamCollector collector = new EventJSONStreamCollector(jsonOutput);
    final EventHandler handler = new EventHandler(new EventValidator(), collector);
    new XmlToJsonConverter().convert(xmlStream, handler);
    Assert.assertTrue(jsonOutput.size() > 0);
    System.out.println(jsonOutput.toString());
  }
}
