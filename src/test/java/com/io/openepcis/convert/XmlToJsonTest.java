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

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.convert.VersionTransformer;
import io.openepcis.convert.collector.EventHandler;
import io.openepcis.convert.collector.JsonEPCISEventCollector;
import io.openepcis.convert.exception.FormatConverterException;
import io.openepcis.convert.util.XMLFormatter;
import io.openepcis.convert.validator.EventValidator;
import io.openepcis.convert.xml.XmlToJsonConverter;
import jakarta.xml.bind.JAXBException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class XmlToJsonTest {
  private VersionTransformer versionTransformer;
  private ByteArrayOutputStream byteArrayOutputStream;
  private InputStream inputStream;

  final XMLFormatter formatter = new XMLFormatter();

  @Before
  public void before() throws JAXBException {
    byteArrayOutputStream = new ByteArrayOutputStream();
    versionTransformer = new VersionTransformer();
  }

  // Test case for Valid XML to JSON-LD conversion
  @Test
  public void objectEventTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/XML/Capture/Documents/ObjectEvent_all_possible_fields.xml");
    final EventHandler handler =
        new EventHandler(new EventValidator(), new JsonEPCISEventCollector(byteArrayOutputStream));
    new XmlToJsonConverter().convert(inputStream, handler);
    Assert.assertTrue(byteArrayOutputStream.toString().length() > 0);
  }

  @Test
  public void aggregationEventTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/XML/Capture/Documents/AggregationEvent_all_possible_fields.xml");
    final EventHandler handler =
        new EventHandler(new EventValidator(), new JsonEPCISEventCollector(byteArrayOutputStream));
    new XmlToJsonConverter().convert(inputStream, handler);
    Assert.assertTrue(byteArrayOutputStream.toString().length() > 0);
  }

  @Test
  public void transformationEventTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/XML/Capture/Documents/TransformationEvent_all_possible_fields.xml");
    final EventHandler handler =
        new EventHandler(new EventValidator(), new JsonEPCISEventCollector(byteArrayOutputStream));
    new XmlToJsonConverter().convert(inputStream, handler);
    Assert.assertTrue(byteArrayOutputStream.toString().length() > 0);
  }

  // Test case for Invalid values for EventHandler
  @Test(expected = FormatConverterException.class)
  public void invalidDocumentTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/JSON/Capture/Documents/TransformationEvent_all_possible_fields.json");
    final EventHandler handler = new EventHandler(null, null);
    new XmlToJsonConverter().convert(inputStream, handler);
  }

  // Test case for Invalid JSON file contents
  @Test(expected = FormatConverterException.class)
  public void fileNotPresentTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("2.0/EPCIS/JSON/Capture/Documents/file_does_not_exist.xml");
    final EventHandler handler = new EventHandler(new EventValidator(), null);
    new XmlToJsonConverter().convert(inputStream, handler);
  }

  // Test to only validate the converted JSON events against JSON-Schema
  @Test
  public void validationTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("2.0/EPCIS/XML/Capture/Documents/ObjectEvent.xml");
    new XmlToJsonConverter().convert(inputStream, new EventHandler(new EventValidator(), null));
  }

  @Test
  public void combinationOfEventsTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/XML/Capture/Documents/Combination_of_different_event.xml");
    final JsonEPCISEventCollector collector = new JsonEPCISEventCollector(byteArrayOutputStream);
    final EventHandler handler = new EventHandler(new EventValidator(), collector);
    new XmlToJsonConverter().convert(inputStream, handler);
    Assert.assertTrue(byteArrayOutputStream.size() > 0);
    Assert.assertTrue(byteArrayOutputStream.toString().contains("eventList"));
  }

  // Test the single event
  @Test
  public void singleEventTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("2.0/EPCIS/XML/Capture/Events/AssociationEvent.xml");
    final JsonEPCISEventCollector collector = new JsonEPCISEventCollector(byteArrayOutputStream);
    final EventHandler handler = new EventHandler(new EventValidator(), collector);
    new XmlToJsonConverter().convert(inputStream, handler);
    Assert.assertTrue(byteArrayOutputStream.size() > 0);
  }

  @Test
  public void sensorElementsWithConvertMethodTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("2.0/EPCIS/XML/Capture/Documents/SensorData_and_extension.xml");
    final InputStream convertedDocument =
        new VersionTransformer()
            .convert(
                inputStream,
                EPCISFormat.XML,
                EPCISVersion.VERSION_2_0_0,
                EPCISFormat.JSON_LD,
                EPCISVersion.VERSION_2_0_0);
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  @Test
  public void jsonConversionScanTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/XML/Capture/Documents/TransformationEvent_with_errorDeclaration.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputStream, EPCISFormat.XML, EPCISFormat.JSON_LD, EPCISVersion.VERSION_2_0_0);
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  @Test
  public void bareStringJsonConversionTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("2.0/EPCIS/XML/Capture/Documents/BareString_information.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputStream, EPCISFormat.XML, EPCISFormat.JSON_LD, EPCISVersion.VERSION_2_0_0);
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  @Test
  public void withSensorDataJsonTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("2.0/EPCIS/XML/Capture/Documents/SensorData_and_extension.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputStream, EPCISFormat.XML, EPCISFormat.JSON_LD, EPCISVersion.VERSION_2_0_0);
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  @Test
  public void document12WithErrorTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "1.2/EPCIS/XML/Capture/Documents/ObjectEvent_with_baseExtension_errorDeclaration.xml");
    final InputStream convertedDocument =
        versionTransformer.convert(
            inputStream, EPCISFormat.XML, EPCISFormat.JSON_LD, EPCISVersion.VERSION_2_0_0);
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }
}
