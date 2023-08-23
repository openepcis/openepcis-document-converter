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
package com.io.openepcis.convert;

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.convert.Conversion;
import io.openepcis.convert.VersionTransformer;
import io.openepcis.convert.collector.EventHandler;
import io.openepcis.convert.collector.XmlEPCISEventCollector;
import io.openepcis.convert.json.JsonToXmlConverter;
import io.openepcis.convert.util.XMLFormatter;
import io.openepcis.convert.validator.EventValidator;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class JsonToXmlTest {

  private ByteArrayOutputStream byteArrayOutputStream;
  private InputStream inputStream;
  final XMLFormatter formatter = new XMLFormatter();

  @Before
  public void before() {
    byteArrayOutputStream = new ByteArrayOutputStream();
  }

  @Test
  public void jsonToXmlObjectEventTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/JSON/Capture/Documents/ObjectEvent_all_possible_fields.json");
    final EventHandler handler =
        new EventHandler(new EventValidator(), new XmlEPCISEventCollector(byteArrayOutputStream));
    new JsonToXmlConverter().convert(inputStream, handler);
    Assert.assertTrue(byteArrayOutputStream.toString().length() > 0);
  }

  @Test
  public void jsonToXmlAggregationEventTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/JSON/Capture/Documents/AggregationEvent_all_possible_fields.json");
    final EventHandler handler =
        new EventHandler(null, new XmlEPCISEventCollector(byteArrayOutputStream));
    new JsonToXmlConverter().convert(inputStream, handler);
    Assert.assertTrue(byteArrayOutputStream.toString().length() > 0);
  }

  // Test to only validate the converted XML events
  @Test
  public void jsonToXmlTransactionEventTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/JSON/Capture/Documents/TransactionEvent_all_possible_fields.json");
    final EventHandler handler =
        new EventHandler(null, new XmlEPCISEventCollector(byteArrayOutputStream));
    new JsonToXmlConverter().convert(inputStream, handler);
    Assert.assertTrue(byteArrayOutputStream.toString().length() > 0);
  }

  @Test
  public void jsonToXmlTransformationEventTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/JSON/Capture/Documents/TransformationEvent_all_possible_fields.json");
    final EventHandler handler =
        new EventHandler(null, new XmlEPCISEventCollector(byteArrayOutputStream));
    new JsonToXmlConverter().convert(inputStream, handler);
    Assert.assertTrue(byteArrayOutputStream.size() > 0);
  }

  // Test the conversion of single EPCIS event in JSON -> XML
  @Test
  public void jsonToXmlTestSingleEvent() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("2.0/EPCIS/JSON/Capture/Events/AssociationEvent.json");
    final EventHandler handler =
        new EventHandler(new EventValidator(), new XmlEPCISEventCollector(byteArrayOutputStream));
    new JsonToXmlConverter().convert(inputStream, handler);
    Assert.assertTrue(byteArrayOutputStream.size() > 0);
  }

  @Test
  public void jsonToXmlVersionTransformerTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/JSON/Capture/Documents/AssociationEvent_all_possible_fields.json");
    final InputStream convertedDocument =
        new VersionTransformer()
            .convert(inputStream,
                b -> b
                    .generateGS1CompliantDocument(false)
                    .fromMediaType(EPCISFormat.JSON_LD)
                    .toMediaType(EPCISFormat.XML)
                    .toVersion(EPCISVersion.VERSION_1_2_0));
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  @Test
  public void jsonToXmlVersionTransformerEventTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("2.0/EPCIS/JSON/Capture/Documents/SensorData_and_extension.json");


    var conversion = Conversion.builder()
        .generateGS1CompliantDocument(false)
        .fromMediaType(EPCISFormat.JSON_LD)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_1_2_0)
        .build();

    final InputStream convertedDocument = new VersionTransformer().convert(inputStream, conversion);
    Assert.assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  @Test
  public void xmlConversionTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/JSON/Capture/Documents/Namespaces_at_different_level.json");

    var conversion = Conversion.builder()
        .generateGS1CompliantDocument(false)
        .fromMediaType(EPCISFormat.JSON_LD)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    final InputStream convertedDocument = new VersionTransformer().convert(inputStream, conversion);
    Assert.assertTrue((IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 00));
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  /*
     Tests for EPCISQueryDocument conversion from JSON to XML
  */
  @Test
  public void combinationOfDifferentEventsTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("2.0/EPCIS/JSON/Query/Combination_of_different_event.json");
    final EventHandler handler =
        new EventHandler(new EventValidator(), new XmlEPCISEventCollector(byteArrayOutputStream));
    new JsonToXmlConverter().convert(inputStream, handler);
    Assert.assertTrue(byteArrayOutputStream.toString().length() > 0);
  }

  @Test
  public void jumbledFieldsOrderTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("2.0/EPCIS/JSON/Query/JumbledFieldsOrder.json");
    final EventHandler handler =
        new EventHandler(new EventValidator(), new XmlEPCISEventCollector(byteArrayOutputStream));
    new JsonToXmlConverter().convert(inputStream, handler);
    Assert.assertTrue(byteArrayOutputStream.toString().length() > 0);
  }

  @Test
  public void objectEventWithAllPossibleFieldsTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("2.0/EPCIS/JSON/Query/ObjectEventWithAllPossibleFields.json");
    final EventHandler handler =
        new EventHandler(new EventValidator(), new XmlEPCISEventCollector(byteArrayOutputStream));
    new JsonToXmlConverter().convert(inputStream, handler);
    Assert.assertTrue(byteArrayOutputStream.toString().length() > 0);
  }

  @Test
  public void sensorDataWithCombinedEventsTest() throws Exception {
    inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("2.0/EPCIS/JSON/Query/SensorData_with_combined_events.json");
    var conversion = Conversion.builder()
        .generateGS1CompliantDocument(false)
        .fromMediaType(EPCISFormat.JSON_LD)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    final InputStream convertedDocument = new VersionTransformer().convert(inputStream, conversion);
    Assert.assertTrue((IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 00));
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }
}
