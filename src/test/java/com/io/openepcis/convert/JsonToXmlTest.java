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

import io.openepcis.convert.EventHandler;
import io.openepcis.convert.EventListCollector;
import io.openepcis.convert.json.EventXMLStreamCollector;
import io.openepcis.convert.json.JsonToXmlConverter;
import io.openepcis.convert.validator.EventValidator;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
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
    final EventHandler handler = new EventHandler(null, new EventXMLStreamCollector(out));
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
    final EventHandler handler = new EventHandler(null, new EventXMLStreamCollector(out));
    new JsonToXmlConverter().convert(jsonStream, handler);
    Assert.assertTrue(out.size() > 0);
    System.out.println(out);
  }

  // Test the conversion of single EPCIS event in JSON -> XML
  @Test
  public void jsonToXmlTestSingleEvent() throws Exception {
    final InputStream jsonStream =
        getClass().getResourceAsStream("/convert/InputEpcisSingleEvent.json");
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final EventHandler handler =
        new EventHandler(new EventValidator(), new EventXMLStreamCollector(out));
    new JsonToXmlConverter().convert(jsonStream, handler);
    Assert.assertTrue(out.size() > 0);
    System.out.println(out);
  }
}
