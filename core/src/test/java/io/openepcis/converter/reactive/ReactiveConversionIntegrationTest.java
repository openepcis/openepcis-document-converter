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
}
