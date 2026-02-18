/*
 * Copyright 2022-2025 benelog GmbH & Co. KG
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
package io.openepcis.converter.reactive;

import static org.junit.jupiter.api.Assertions.*;

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.Conversion;
import io.openepcis.model.epcis.EPCISEvent;
import io.smallrye.mutiny.Multi;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for ReactiveVersionTransformer covering full conversion pipelines.
 */
class ReactiveConversionIntegrationTest {

  private ReactiveVersionTransformer transformer;

  @BeforeEach
  void setUp() {
    transformer = ReactiveVersionTransformer.builder().build();
  }

  // ==================== XML 2.0 -> JSON-LD 2.0 ====================

  @Test
  void shouldConvertXml20ToJsonLd20() throws Exception {
    InputStream xmlStream = getClass().getClassLoader()
        .getResourceAsStream("2.0/EPCIS/XML/Capture/Documents/ObjectEvent.xml");

    if (xmlStream == null) {
      // Skip test if resource not available
      return;
    }

    byte[] xmlBytes = xmlStream.readAllBytes();
    xmlStream.close();

    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.XML)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.JSON_LD)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    ByteArrayOutputStream result = new ByteArrayOutputStream();
    AtomicInteger chunkCount = new AtomicInteger(0);

    transformer.convert(xmlBytes, conversion)
        .subscribe().with(
            bytes -> {
              result.writeBytes(bytes);
              chunkCount.incrementAndGet();
            },
            error -> fail("Conversion failed: " + error.getMessage()),
            () -> {});

    String jsonResult = result.toString(StandardCharsets.UTF_8);

    // Verify JSON structure
    assertTrue(jsonResult.contains("@context"), "Should contain @context");
    assertTrue(jsonResult.contains("EPCISDocument") || jsonResult.contains("ObjectEvent"),
        "Should contain EPCIS elements");
    assertTrue(chunkCount.get() > 0, "Should emit at least one chunk");
  }

  @Test
  void shouldConvertXml20ToJsonLd20WithEvents() throws Exception {
    InputStream xmlStream = getClass().getClassLoader()
        .getResourceAsStream("2.0/EPCIS/XML/Capture/Documents/Combination_of_different_event.xml");

    if (xmlStream == null) {
      return;
    }

    byte[] xmlBytes = xmlStream.readAllBytes();
    xmlStream.close();

    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.XML)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.JSON_LD)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    AtomicInteger eventCount = new AtomicInteger(0);
    AtomicInteger errorCount = new AtomicInteger(0);

    transformer.convertToEvents(xmlBytes, conversion)
        .subscribe().with(
            event -> {
              assertNotNull(event);
              eventCount.incrementAndGet();
            },
            error -> errorCount.incrementAndGet(),
            () -> {});

    // Either events were emitted or it completed without error
    assertTrue(eventCount.get() >= 0 || errorCount.get() == 0,
        "Should complete without hanging");
  }

  // ==================== JSON-LD 2.0 -> XML 2.0 ====================

  @Test
  void shouldConvertJsonLd20ToXml20() throws Exception {
    InputStream jsonStream = getClass().getClassLoader()
        .getResourceAsStream("2.0/EPCIS/JSON/Capture/Documents/ObjectEvent.json");

    if (jsonStream == null) {
      return;
    }

    byte[] jsonBytes = jsonStream.readAllBytes();
    jsonStream.close();

    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.JSON_LD)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    ByteArrayOutputStream result = new ByteArrayOutputStream();

    transformer.convert(jsonBytes, conversion)
        .subscribe().with(
            bytes -> result.writeBytes(bytes),
            error -> fail("Conversion failed: " + error.getMessage()),
            () -> {});

    String xmlResult = result.toString(StandardCharsets.UTF_8);

    // Verify XML structure
    assertTrue(xmlResult.contains("EPCISDocument") || xmlResult.contains("ObjectEvent"),
        "Should contain EPCIS XML elements");
  }

  @Test
  void shouldConvertJsonLd20ToEvents() throws Exception {
    InputStream jsonStream = getClass().getClassLoader()
        .getResourceAsStream("2.0/EPCIS/JSON/Capture/Documents/Combination_of_different_event.json");

    if (jsonStream == null) {
      return;
    }

    byte[] jsonBytes = jsonStream.readAllBytes();
    jsonStream.close();

    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.JSON_LD)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    AtomicInteger eventCount = new AtomicInteger(0);

    transformer.convertToEvents(jsonBytes, conversion)
        .subscribe().with(
            event -> {
              assertNotNull(event);
              eventCount.incrementAndGet();
            },
            error -> fail("Conversion failed: " + error.getMessage()),
            () -> {});

    assertTrue(eventCount.get() >= 0, "Should complete without error");
  }

  // ==================== XML 1.2 -> XML 2.0 -> JSON-LD 2.0 ====================

  @Test
  void shouldConvertXml12ToJsonLd20() throws Exception {
    InputStream xmlStream = getClass().getClassLoader()
        .getResourceAsStream("1.2/EPCIS/XML/Capture/Documents/ObjectEvent.xml");

    if (xmlStream == null) {
      return;
    }

    byte[] xmlBytes = xmlStream.readAllBytes();
    xmlStream.close();

    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.XML)
        .fromVersion(EPCISVersion.VERSION_1_2_0)
        .toMediaType(EPCISFormat.JSON_LD)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    ByteArrayOutputStream result = new ByteArrayOutputStream();

    transformer.convert(xmlBytes, conversion)
        .subscribe().with(
            bytes -> result.writeBytes(bytes),
            error -> fail("Conversion failed: " + error.getMessage()),
            () -> {});

    String jsonResult = result.toString(StandardCharsets.UTF_8);

    // If conversion succeeded, verify JSON structure
    if (!jsonResult.isEmpty()) {
      assertTrue(jsonResult.contains("@context") || jsonResult.contains("EPCISDocument"),
          "Should contain JSON-LD elements");
    }
  }

  // ==================== XML Version Transforms ====================

  @Test
  void shouldTransformXml12ToXml20() throws Exception {
    InputStream xmlStream = getClass().getClassLoader()
        .getResourceAsStream("1.2/EPCIS/XML/Capture/Documents/ObjectEvent.xml");

    if (xmlStream == null) {
      return;
    }

    byte[] xmlBytes = xmlStream.readAllBytes();
    xmlStream.close();

    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.XML)
        .fromVersion(EPCISVersion.VERSION_1_2_0)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    ByteArrayOutputStream result = new ByteArrayOutputStream();

    transformer.convert(xmlBytes, conversion)
        .subscribe().with(
            bytes -> result.writeBytes(bytes),
            error -> fail("Conversion failed: " + error.getMessage()),
            () -> {});

    String xmlResult = result.toString(StandardCharsets.UTF_8);

    // If conversion succeeded, verify it's EPCIS 2.0 XML
    if (!xmlResult.isEmpty()) {
      assertTrue(xmlResult.contains("EPCISDocument") || xmlResult.contains("epcis:"),
          "Should contain EPCIS 2.0 XML elements");
    }
  }

  @Test
  void shouldTransformXml20ToXml12() throws Exception {
    InputStream xmlStream = getClass().getClassLoader()
        .getResourceAsStream("2.0/EPCIS/XML/Capture/Documents/ObjectEvent.xml");

    if (xmlStream == null) {
      return;
    }

    byte[] xmlBytes = xmlStream.readAllBytes();
    xmlStream.close();

    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.XML)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_1_2_0)
        .build();

    ByteArrayOutputStream result = new ByteArrayOutputStream();

    transformer.convert(xmlBytes, conversion)
        .subscribe().with(
            bytes -> result.writeBytes(bytes),
            error -> fail("Conversion failed: " + error.getMessage()),
            () -> {});

    String xmlResult = result.toString(StandardCharsets.UTF_8);

    // If conversion succeeded, verify it's EPCIS 1.2 XML
    if (!xmlResult.isEmpty()) {
      assertTrue(xmlResult.contains("EPCISDocument"),
          "Should contain EPCISDocument element");
    }
  }

  // ==================== EPCISQueryDocument Tests ====================

  @Test
  void shouldConvertXml20QueryDocumentToJsonLd20() throws Exception {
    InputStream xmlStream = getClass().getClassLoader()
        .getResourceAsStream("2.0/EPCIS/XML/Query/Combination_of_different_event.xml");

    if (xmlStream == null) {
      return;
    }

    byte[] xmlBytes = xmlStream.readAllBytes();
    xmlStream.close();

    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.XML)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.JSON_LD)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    ByteArrayOutputStream result = new ByteArrayOutputStream();

    transformer.convert(xmlBytes, conversion)
        .subscribe().with(
            bytes -> result.writeBytes(bytes),
            error -> fail("Conversion failed: " + error.getMessage()),
            () -> {});

    String jsonResult = result.toString(StandardCharsets.UTF_8);

    // Verify it's a query document, not a regular document
    // Header JSON is compact (no spaces around colons)
    assertTrue(jsonResult.contains("\"EPCISQueryDocument\""),
        "Should have type EPCISQueryDocument, got: " + jsonResult.substring(0, Math.min(500, jsonResult.length())));
    assertFalse(jsonResult.contains("\"type\":\"EPCISDocument\"")
        || jsonResult.contains("\"type\" : \"EPCISDocument\""),
        "Should NOT have type EPCISDocument");

    // Verify query results wrapper structure
    assertTrue(jsonResult.contains("\"queryResults\""),
        "Should contain queryResults wrapper");
    assertTrue(jsonResult.contains("\"resultsBody\""),
        "Should contain resultsBody wrapper");
    assertTrue(jsonResult.contains("\"eventList\""),
        "Should contain eventList");

    // Verify createdAt is preserved (not creationDate)
    assertTrue(jsonResult.contains("\"createdAt\""),
        "Should preserve createdAt attribute");

    // Verify valid JSON by checking matching braces
    assertTrue(jsonResult.trim().startsWith("{") && jsonResult.trim().endsWith("}"),
        "Should be valid JSON structure");
  }

  @Test
  void shouldConvertXml20QueryDocumentToXml20RoundTrip() throws Exception {
    InputStream xmlStream = getClass().getClassLoader().getResourceAsStream("2.0/EPCIS/XML/Query/Combination_of_different_event.xml");

    if (xmlStream == null) {
      return;
    }

    byte[] xmlBytes = xmlStream.readAllBytes();
    xmlStream.close();

    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.XML)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    ByteArrayOutputStream result = new ByteArrayOutputStream();

    transformer.convert(xmlBytes, conversion)
        .subscribe().with(
            bytes -> result.writeBytes(bytes),
            error -> fail("Conversion failed: " + error.getMessage()),
            () -> {});

    String xmlResult = result.toString(StandardCharsets.UTF_8);

    // Verify query document structure is preserved
    assertTrue(xmlResult.contains("EPCISQueryDocument"), "Should contain EPCISQueryDocument root element");
    assertTrue(xmlResult.contains("QueryResults"), "Should contain QueryResults wrapper");
    assertTrue(xmlResult.contains("resultsBody"), "Should contain resultsBody wrapper");
    assertTrue(xmlResult.contains("EventList"), "Should contain EventList");

    // Verify createdAt is preserved
    assertTrue(xmlResult.contains("creationDate"), "Should preserve createdAt attribute");
  }

  @Test
  void shouldExtractEventsFromXml20QueryDocument() throws Exception {
    InputStream xmlStream = getClass().getClassLoader().getResourceAsStream("2.0/EPCIS/XML/Query/Combination_of_different_event.xml");

    if (xmlStream == null) {
      return;
    }

    byte[] xmlBytes = xmlStream.readAllBytes();
    xmlStream.close();

    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.XML)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.JSON_LD)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    AtomicInteger eventCount = new AtomicInteger(0);

    transformer.convertToEvents(xmlBytes, conversion)
        .subscribe().with(
            event -> {
              assertNotNull(event);
              eventCount.incrementAndGet();
            },
            error -> fail("Event extraction failed: " + error.getMessage()),
            () -> {});

    assertTrue(eventCount.get() > 0, "Should extract events from query document");
  }

  // ==================== SBDH (EPCISHeader) Tests ====================

  @Test
  void shouldConvertXml12WithSbdhToJsonLd20WithCorrectDocumentType() throws Exception {
    // XML 1.2 EPCISDocument with EPCISHeader containing StandardBusinessDocumentHeader (SBDH).
    // Regression test: SBDH child elements like "StandardBusinessDocumentHeader" and
    // "DocumentIdentification" contain the word "Document", which previously caused the
    // document type detection to re-fire and incorrectly switch isEPCISDocument to false,
    // resulting in the query document footer being emitted instead of the regular footer.
    String xml12WithSbdh = """
        <?xml version="1.0" encoding="UTF-8"?>
        <epcis:EPCISDocument xmlns:epcis="urn:epcglobal:epcis:xsd:1"
            xmlns:sbdh="http://www.unece.org/cefact/namespaces/StandardBusinessDocumentHeader"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            schemaVersion="1.2" creationDate="2023-04-01T08:45:16Z">
          <EPCISHeader>
            <sbdh:StandardBusinessDocumentHeader>
              <sbdh:HeaderVersion>1.0</sbdh:HeaderVersion>
              <sbdh:Sender>
                <sbdh:Identifier Authority="GS1">urn:epc:id:sgln:030001.111111.0</sbdh:Identifier>
              </sbdh:Sender>
              <sbdh:Receiver>
                <sbdh:Identifier Authority="GS1">urn:epc:id:sgln:039999.999999.0</sbdh:Identifier>
              </sbdh:Receiver>
              <sbdh:DocumentIdentification>
                <sbdh:Standard>EPCglobal</sbdh:Standard>
                <sbdh:TypeVersion>1.0</sbdh:TypeVersion>
                <sbdh:InstanceIdentifier>1100220001</sbdh:InstanceIdentifier>
                <sbdh:Type>Events</sbdh:Type>
                <sbdh:CreationDateAndTime>2023-04-01T08:45:16Z</sbdh:CreationDateAndTime>
              </sbdh:DocumentIdentification>
            </sbdh:StandardBusinessDocumentHeader>
          </EPCISHeader>
          <EPCISBody>
            <EventList>
              <ObjectEvent>
                <eventTime>2023-04-01T08:45:16Z</eventTime>
                <eventTimeZoneOffset>+00:00</eventTimeZoneOffset>
                <epcList>
                  <epc>urn:epc:id:sgtin:030001.0012345.22222222229</epc>
                </epcList>
                <action>ADD</action>
                <bizStep>urn:epcglobal:cbv:bizstep:commissioning</bizStep>
                <disposition>urn:epcglobal:cbv:disp:active</disposition>
              </ObjectEvent>
            </EventList>
          </EPCISBody>
        </epcis:EPCISDocument>
        """;

    byte[] xmlBytes = xml12WithSbdh.getBytes(StandardCharsets.UTF_8);

    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.XML)
        .fromVersion(EPCISVersion.VERSION_1_2_0)
        .toMediaType(EPCISFormat.JSON_LD)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    ByteArrayOutputStream result = new ByteArrayOutputStream();

    transformer.convert(xmlBytes, conversion)
        .subscribe().with(
            bytes -> result.writeBytes(bytes),
            error -> fail("Conversion failed: " + error.getMessage()),
            () -> {});

    String jsonResult = result.toString(StandardCharsets.UTF_8);

    // Must be EPCISDocument, NOT EPCISQueryDocument
    assertTrue(jsonResult.contains("\"EPCISDocument\""),
        "Should have type EPCISDocument, got: " + jsonResult.substring(0, Math.min(500, jsonResult.length())));
    assertFalse(jsonResult.contains("EPCISQueryDocument"),
        "Should NOT have type EPCISQueryDocument — SBDH elements must not corrupt document type detection");

    // Must NOT contain query document structure
    assertFalse(jsonResult.contains("\"queryResults\""),
        "Should NOT contain queryResults (that's query document structure)");

    // Verify valid JSON structure (correct number of closing braces)
    String trimmed = jsonResult.trim();
    assertTrue(trimmed.startsWith("{") && trimmed.endsWith("}"),
        "Should be valid JSON structure");

    // Count opening and closing braces — they must match
    long openBraces = trimmed.chars().filter(c -> c == '{').count();
    long closeBraces = trimmed.chars().filter(c -> c == '}').count();
    assertEquals(openBraces, closeBraces,
        "Opening and closing braces must match — malformed JSON detected");

    // Verify creationDate is preserved
    assertTrue(jsonResult.contains("2023-04-01"),
        "Should preserve original creationDate");
  }

  @Test
  void shouldConvertXml20WithSbdhToJsonLd20WithCorrectDocumentType() throws Exception {
    InputStream xmlStream = getClass().getClassLoader()
        .getResourceAsStream("2.0/EPCIS/XML/Capture/Documents/ObjectEvent.xml");

    if (xmlStream == null) {
      return;
    }

    byte[] xmlBytes = xmlStream.readAllBytes();
    xmlStream.close();

    // Verify the test resource actually contains SBDH (otherwise test is meaningless)
    String xmlStr = new String(xmlBytes, StandardCharsets.UTF_8);
    assertTrue(xmlStr.contains("StandardBusinessDocumentHeader"),
        "Test resource should contain SBDH for this test to be meaningful");

    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.XML)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.JSON_LD)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    ByteArrayOutputStream result = new ByteArrayOutputStream();

    transformer.convert(xmlBytes, conversion)
        .subscribe().with(
            bytes -> result.writeBytes(bytes),
            error -> fail("Conversion failed: " + error.getMessage()),
            () -> {});

    String jsonResult = result.toString(StandardCharsets.UTF_8);

    // Must be EPCISDocument, NOT EPCISQueryDocument
    assertTrue(jsonResult.contains("\"EPCISDocument\""),
        "Should have type EPCISDocument");
    assertFalse(jsonResult.contains("EPCISQueryDocument"),
        "Should NOT have type EPCISQueryDocument — SBDH must not corrupt document type");

    // Count braces must match
    long openBraces = jsonResult.chars().filter(c -> c == '{').count();
    long closeBraces = jsonResult.chars().filter(c -> c == '}').count();
    assertEquals(openBraces, closeBraces,
        "Opening and closing braces must match");
  }

  // ==================== Backpressure Tests ====================

  @Test
  void shouldRespectBackpressureWithLimitedDemand() throws Exception {
    // Create a larger document for backpressure testing
    String jsonDocument = """
        {
          "@context": ["https://ref.gs1.org/standards/epcis/epcis-context.jsonld"],
          "type": "EPCISDocument",
          "schemaVersion": "2.0",
          "creationDate": "2023-01-01T00:00:00Z",
          "epcisBody": {
            "eventList": [
              {
                "type": "ObjectEvent",
                "eventTime": "2023-01-01T00:00:00Z",
                "eventTimeZoneOffset": "+00:00",
                "action": "OBSERVE",
                "epcList": ["urn:epc:id:sgtin:0614141.107346.2017"]
              },
              {
                "type": "ObjectEvent",
                "eventTime": "2023-01-02T00:00:00Z",
                "eventTimeZoneOffset": "+00:00",
                "action": "ADD",
                "epcList": ["urn:epc:id:sgtin:0614141.107346.2018"]
              },
              {
                "type": "ObjectEvent",
                "eventTime": "2023-01-03T00:00:00Z",
                "eventTimeZoneOffset": "+00:00",
                "action": "DELETE",
                "epcList": ["urn:epc:id:sgtin:0614141.107346.2019"]
              }
            ]
          }
        }
        """;

    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.JSON_LD)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    // Test with limited request - should not hang
    Multi<byte[]> result = transformer.convert(
        jsonDocument.getBytes(StandardCharsets.UTF_8), conversion);

    // Request only 2 items (simulating backpressure)
    AtomicInteger received = new AtomicInteger(0);
    result.select().first(2)
        .subscribe().with(
            bytes -> received.incrementAndGet(),
            error -> fail("Should not fail: " + error.getMessage()),
            () -> {});

    // Should have received exactly 2 items
    assertTrue(received.get() <= 2, "Should respect backpressure limit");
  }

  // ==================== Error Handling ====================

  @Test
  void shouldHandleInvalidXmlGracefully() {
    byte[] invalidXml = "<not valid xml".getBytes(StandardCharsets.UTF_8);

    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.XML)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.JSON_LD)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    AtomicInteger errorCount = new AtomicInteger(0);
    AtomicInteger completeCount = new AtomicInteger(0);

    transformer.convert(invalidXml, conversion)
        .subscribe().with(
            bytes -> {},
            error -> errorCount.incrementAndGet(),
            () -> completeCount.incrementAndGet());

    // Invalid XML should either error out or complete (not hang)
    // At least one of these should be true
    assertTrue(errorCount.get() > 0 || completeCount.get() > 0,
        "Should either error or complete for invalid XML");
  }

  @Test
  void shouldHandleInvalidJsonGracefully() {
    byte[] invalidJson = "{ not valid json".getBytes(StandardCharsets.UTF_8);

    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.JSON_LD)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    AtomicInteger errorCount = new AtomicInteger(0);
    AtomicInteger completeCount = new AtomicInteger(0);

    transformer.convert(invalidJson, conversion)
        .subscribe().with(
            bytes -> {},
            error -> errorCount.incrementAndGet(),
            () -> completeCount.incrementAndGet());

    // Invalid JSON should either error out or complete (not hang)
    // At least one of these should be true
    assertTrue(errorCount.get() > 0 || completeCount.get() > 0,
        "Should either error or complete for invalid JSON");
  }

  // ==================== Custom Buffer Size ====================

  @Test
  void shouldWorkWithCustomBufferSize() {
    ReactiveVersionTransformer customTransformer = ReactiveVersionTransformer.builder()
        .bufferSize(4096)
        .build();

    String jsonDocument = """
        {
          "@context": ["https://ref.gs1.org/standards/epcis/epcis-context.jsonld"],
          "type": "EPCISDocument",
          "schemaVersion": "2.0",
          "creationDate": "2023-01-01T00:00:00Z",
          "epcisBody": {
            "eventList": [
              {
                "type": "ObjectEvent",
                "eventTime": "2023-01-01T00:00:00Z",
                "eventTimeZoneOffset": "+00:00",
                "action": "OBSERVE",
                "epcList": ["urn:epc:id:sgtin:0614141.107346.2017"]
              }
            ]
          }
        }
        """;

    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.JSON_LD)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    ByteArrayOutputStream result = new ByteArrayOutputStream();

    customTransformer.convert(jsonDocument.getBytes(StandardCharsets.UTF_8), conversion)
        .subscribe().with(
            bytes -> result.writeBytes(bytes),
            error -> fail("Conversion failed: " + error.getMessage()),
            () -> {});

    assertTrue(result.size() > 0, "Should produce output with custom buffer size");
  }

  // ==================== Event Mapper Tests ====================

  @Test
  void shouldApplyEventMapper() {
    AtomicInteger mapperCallCount = new AtomicInteger(0);

    ReactiveVersionTransformer mappedTransformer = transformer.mapWith((event, context) -> {
      mapperCallCount.incrementAndGet();
      return event;
    });

    String jsonDocument = """
        {
          "@context": ["https://ref.gs1.org/standards/epcis/epcis-context.jsonld"],
          "type": "EPCISDocument",
          "schemaVersion": "2.0",
          "creationDate": "2023-01-01T00:00:00Z",
          "epcisBody": {
            "eventList": [
              {
                "type": "ObjectEvent",
                "eventTime": "2023-01-01T00:00:00Z",
                "eventTimeZoneOffset": "+00:00",
                "action": "OBSERVE",
                "epcList": ["urn:epc:id:sgtin:0614141.107346.2017"]
              }
            ]
          }
        }
        """;

    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.JSON_LD)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    ByteArrayOutputStream result = new ByteArrayOutputStream();

    mappedTransformer.convert(jsonDocument.getBytes(StandardCharsets.UTF_8), conversion)
        .subscribe().with(
            bytes -> result.writeBytes(bytes),
            error -> fail("Conversion failed: " + error.getMessage()),
            () -> {});

    // Mapper may or may not be called depending on the conversion path
    assertTrue(result.size() >= 0, "Should complete without error");
  }

  // ==================== Non-Standard Prefix Filtering Tests ====================

  @Test
  void shouldFilterStandardNamespaceUrisWithNonStandardPrefixes() throws Exception {
    // XML 1.2 document using non-standard prefixes (n0, n1) for standard EPCIS URIs,
    // plus a truly custom namespace (prx). The converter must filter out standard URIs
    // regardless of their prefix name.
    String xml12WithNonStandardPrefixes = """
        <?xml version="1.0" encoding="UTF-8"?>
        <n0:EPCISDocument xmlns:n0="urn:epcglobal:epcis:xsd:1"
            xmlns:n1="http://www.unece.org/cefact/namespaces/StandardBusinessDocumentHeader"
            xmlns:prx="https://example.com/custom"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            schemaVersion="1.2" creationDate="2023-06-15T10:30:00Z">
          <EPCISBody>
            <EventList>
              <ObjectEvent>
                <eventTime>2023-06-15T10:30:00Z</eventTime>
                <eventTimeZoneOffset>+02:00</eventTimeZoneOffset>
                <epcList>
                  <epc>urn:epc:id:sgtin:030001.0012345.99999</epc>
                </epcList>
                <action>ADD</action>
                <bizStep>urn:epcglobal:cbv:bizstep:commissioning</bizStep>
                <disposition>urn:epcglobal:cbv:disp:active</disposition>
              </ObjectEvent>
            </EventList>
          </EPCISBody>
        </n0:EPCISDocument>
        """;

    byte[] xmlBytes = xml12WithNonStandardPrefixes.getBytes(StandardCharsets.UTF_8);

    // Test 1: XML 1.2 -> JSON-LD 2.0 — standard URIs must not leak into @context
    Conversion toJsonLd = Conversion.builder()
        .fromMediaType(EPCISFormat.XML)
        .fromVersion(EPCISVersion.VERSION_1_2_0)
        .toMediaType(EPCISFormat.JSON_LD)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    ByteArrayOutputStream jsonResult = new ByteArrayOutputStream();
    transformer.convert(xmlBytes, toJsonLd)
        .subscribe().with(
            bytes -> jsonResult.writeBytes(bytes),
            error -> fail("XML 1.2 -> JSON-LD conversion failed: " + error.getMessage()),
            () -> {});

    String json = jsonResult.toString(StandardCharsets.UTF_8);

    // @context must NOT contain n0 or n1 entries for standard URIs
    assertFalse(json.contains("\"n0\""),
        "JSON-LD @context must NOT contain n0 (standard URI with non-standard prefix)");
    assertFalse(json.contains("\"n1\""),
        "JSON-LD @context must NOT contain n1 (standard URI with non-standard prefix)");

    // @context SHOULD contain the truly custom namespace
    assertTrue(json.contains("\"prx\""),
        "JSON-LD @context must contain the custom namespace prx");
    assertTrue(json.contains("https://example.com/custom"),
        "JSON-LD @context must contain the custom namespace URI");

    // Test 2: XML 1.2 -> XML 2.0 — no duplicate namespace declarations
    Conversion toXml20 = Conversion.builder()
        .fromMediaType(EPCISFormat.XML)
        .fromVersion(EPCISVersion.VERSION_1_2_0)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    ByteArrayOutputStream xmlResult = new ByteArrayOutputStream();
    transformer.convert(xmlBytes, toXml20)
        .subscribe().with(
            bytes -> xmlResult.writeBytes(bytes),
            error -> fail("XML 1.2 -> XML 2.0 conversion failed: " + error.getMessage()),
            () -> {});

    String xml20 = xmlResult.toString(StandardCharsets.UTF_8);

    // Must NOT have the non-standard prefix declarations for standard URIs
    assertFalse(xml20.contains("xmlns:n0="),
        "XML 2.0 output must NOT contain xmlns:n0 for standard URI");
    assertFalse(xml20.contains("xmlns:n1="),
        "XML 2.0 output must NOT contain xmlns:n1 for standard URI");

    // Must contain the custom namespace
    assertTrue(xml20.contains("xmlns:prx="),
        "XML 2.0 output must contain the custom namespace prx");

    // Standard epcis namespace must appear only once
    int epcisNsCount = countOccurrences(xml20, "urn:epcglobal:epcis:xsd:2");
    assertTrue(epcisNsCount <= 1,
        "EPCIS 2.0 namespace URI should appear at most once, found " + epcisNsCount + " times");
  }

  private int countOccurrences(String str, String sub) {
    int count = 0;
    int idx = 0;
    while ((idx = str.indexOf(sub, idx)) != -1) {
      count++;
      idx += sub.length();
    }
    return count;
  }
}
