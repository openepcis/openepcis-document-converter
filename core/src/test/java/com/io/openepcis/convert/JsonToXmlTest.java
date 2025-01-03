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
package com.io.openepcis.convert;

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.Conversion;
import io.openepcis.converter.VersionTransformer;
import io.openepcis.converter.collector.EventHandler;
import io.openepcis.converter.collector.JsonEPCISEventCollector;
import io.openepcis.converter.collector.XmlEPCISEventCollector;
import io.openepcis.converter.common.GS1FormatSupport;
import io.openepcis.converter.json.JsonToXmlConverter;
import io.openepcis.converter.util.XMLFormatter;
import io.openepcis.converter.validator.EventValidator;
import io.openepcis.converter.xml.XmlToJsonConverter;
import io.openepcis.model.epcis.format.FormatPreference;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertTrue;
public class JsonToXmlTest {

  final XMLFormatter formatter = new XMLFormatter();


  @Test
  void jsonToXmlObjectEventTest() throws Exception {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    InputStream inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/JSON/Capture/Documents/ObjectEvent_all_possible_fields.json");
    try (final EventHandler handler =
        new EventHandler(new EventValidator(), new XmlEPCISEventCollector(byteArrayOutputStream))) {
      new JsonToXmlConverter().convert(inputStream, handler);
      System.out.println(byteArrayOutputStream);
      assertTrue(byteArrayOutputStream.toString().length() > 0);
    }
  }

  @Test
  void jsonToXmlAggregationEventTest() throws Exception {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    InputStream inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/JSON/Capture/Documents/AggregationEvent_all_possible_fields.json");
    try (final EventHandler handler =
                 new EventHandler(new EventValidator(), new XmlEPCISEventCollector(byteArrayOutputStream))) {
      new JsonToXmlConverter().convert(inputStream, handler);
      assertTrue(byteArrayOutputStream.toString().length() > 0);
    }
  }

  // Test to only validate the converted XML events
  @Test
  void jsonToXmlTransactionEventTest() throws Exception {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    InputStream inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/JSON/Capture/Documents/TransactionEvent_all_possible_fields.json");
    try (final EventHandler handler =
                 new EventHandler(new EventValidator(), new XmlEPCISEventCollector(byteArrayOutputStream))) {
      new JsonToXmlConverter().convert(inputStream, handler);
      assertTrue(byteArrayOutputStream.toString().length() > 0);
    }
  }

  @Test
  void jsonToXmlTransformationEventTest() throws Exception {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    InputStream inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "2.0/EPCIS/JSON/Capture/Documents/TransformationEvent_all_possible_fields.json");
    try (final EventHandler handler =
                 new EventHandler(new EventValidator(), new XmlEPCISEventCollector(byteArrayOutputStream))) {
      new JsonToXmlConverter().convert(inputStream, handler);
      assertTrue(byteArrayOutputStream.size() > 0);
    }
  }

  // Test the conversion of single EPCIS event in JSON -> XML
  @Test
  void jsonToXmlTestSingleEvent() throws Exception {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    InputStream inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("2.0/EPCIS/JSON/Capture/Events/AssociationEvent.json");
    try (final EventHandler handler =
                 new EventHandler(new EventValidator(), new XmlEPCISEventCollector(byteArrayOutputStream))) {
      new JsonToXmlConverter().convert(inputStream, handler);
      assertTrue(byteArrayOutputStream.size() > 0);
    }
  }

  @Test
  void jsonToXmlVersionTransformerTest() throws Exception {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    InputStream inputStream =
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
    assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  @Test
  void jsonToXmlVersionTransformerEventTest() throws Exception {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    InputStream inputStream =
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
    assertTrue(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 0);
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }

  @Test
  void xmlConversionTest() throws Exception {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    InputStream inputStream =
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
    assertTrue((IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 00));
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
  void combinationOfDifferentEventsTest() throws Exception {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    InputStream inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("2.0/EPCIS/JSON/Query/Combination_of_different_event.json");
    try (final EventHandler handler =
                 new EventHandler(new EventValidator(), new XmlEPCISEventCollector(byteArrayOutputStream))) {
      new JsonToXmlConverter().convert(inputStream, handler);
      assertTrue(byteArrayOutputStream.toString().length() > 0);
    }
  }

  @Test
  void jumbledFieldsOrderTest() throws Exception {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    InputStream inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("2.0/EPCIS/JSON/Query/JumbledFieldsOrder.json");
    try (final EventHandler handler =
                 new EventHandler(new EventValidator(), new XmlEPCISEventCollector(byteArrayOutputStream))) {
      new JsonToXmlConverter().convert(inputStream, handler);
      assertTrue(byteArrayOutputStream.toString().length() > 0);
    }
  }

  @Test
  void objectEventWithAllPossibleFieldsTest() throws Exception {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    InputStream inputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("2.0/EPCIS/JSON/Query/ObjectEventWithAllPossibleFields.json");
    final EventHandler handler =
        new EventHandler(new EventValidator(), new XmlEPCISEventCollector(byteArrayOutputStream));
    new JsonToXmlConverter().convert(inputStream, handler);
    assertTrue(byteArrayOutputStream.toString().length() > 0);
  }

  @Test
  void sensorDataWithCombinedEventsTest() throws Exception {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    InputStream inputStream =
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
    assertTrue((IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).length() > 00));
    try {
      convertedDocument.close();
    } catch (IOException ignore) {
      // ignored
    }
  }
}
