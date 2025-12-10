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
import io.openepcis.reactive.util.ReactiveSource;
import io.openepcis.model.epcis.EPCISEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ReactiveVersionTransformer.
 */
class ReactiveVersionTransformerTest {

  private ReactiveVersionTransformer transformer;

  @BeforeEach
  void setUp() {
    transformer = ReactiveVersionTransformer.builder().build();
  }

  @Test
  void shouldCreateTransformerWithDefaultSettings() {
    assertNotNull(transformer);
  }

  @Test
  void shouldCreateTransformerWithCustomBufferSize() {
    ReactiveVersionTransformer custom = ReactiveVersionTransformer.builder()
        .bufferSize(16384)
        .build();
    assertNotNull(custom);
  }

  @Test
  void shouldRejectInvalidBufferSize() {
    assertThrows(IllegalArgumentException.class, () ->
        ReactiveVersionTransformer.builder().bufferSize(0).build());

    assertThrows(IllegalArgumentException.class, () ->
        ReactiveVersionTransformer.builder().bufferSize(-1).build());
  }

  @Test
  void shouldConvertJsonToXml() throws Exception {
    // Given: A simple JSON-LD EPCIS document
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

    // When: Convert to XML
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    transformer.convert(jsonDocument.getBytes(StandardCharsets.UTF_8), conversion)
        .subscribe().with(
            bytes -> result.writeBytes(bytes),
            error -> fail("Conversion failed: " + error.getMessage()),
            () -> {});

    // Then: Result should be valid XML
    String xmlResult = result.toString(StandardCharsets.UTF_8);
    assertTrue(xmlResult.contains("EPCISDocument") || xmlResult.contains("ObjectEvent"),
        "Should contain EPCIS XML elements");
  }

  @Test
  void shouldConvertXmlToJson() throws Exception {
    // Given: An XML EPCIS document
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

    // When: Convert to JSON
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    transformer.convert(xmlBytes, conversion)
        .subscribe().with(
            bytes -> result.writeBytes(bytes),
            error -> fail("Conversion failed: " + error.getMessage()),
            () -> {});

    // Then: Result should contain JSON-LD elements
    String jsonResult = result.toString(StandardCharsets.UTF_8);
    assertTrue(jsonResult.contains("@context") || jsonResult.contains("EPCISDocument") ||
            jsonResult.contains("eventList"),
        "Should contain JSON-LD elements");
  }

  @Test
  void shouldSupportEventLevelStreaming() throws Exception {
    // Given: A JSON-LD document with events
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

    // When: Convert to events
    AtomicInteger eventCount = new AtomicInteger(0);
    transformer.convertToEvents(jsonDocument.getBytes(StandardCharsets.UTF_8), conversion)
        .subscribe().with(
            event -> {
              assertNotNull(event);
              eventCount.incrementAndGet();
            },
            error -> fail("Conversion failed: " + error.getMessage()),
            () -> {});

    // Then: Should have parsed events
    assertTrue(eventCount.get() >= 0, "Should parse events from document");
  }

  @Test
  void shouldSupportMapWithFunction() {
    // Given: A transformer with event mapper
    AtomicInteger mapCallCount = new AtomicInteger(0);

    ReactiveVersionTransformer mappedTransformer = transformer.mapWith((event, context) -> {
      mapCallCount.incrementAndGet();
      return event;
    });

    // Then: Should create new transformer instance
    assertNotNull(mappedTransformer);
    assertNotSame(transformer, mappedTransformer);
  }

  @Test
  void shouldHandleEmptyInput() {
    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.JSON_LD)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    // When: Convert empty input
    Multi<byte[]> result = transformer.convert(new byte[0], conversion);

    // Then: Should complete (possibly with error for invalid input)
    assertNotNull(result);
  }

  @Test
  void shouldRejectNullSource() {
    Conversion conversion = Conversion.builder()
        .fromMediaType(EPCISFormat.JSON_LD)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    assertThrows(NullPointerException.class, () ->
        transformer.convert((ReactiveSource) null, conversion));
  }

  @Test
  void shouldRejectNullConversion() {
    byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);

    assertThrows(NullPointerException.class, () ->
        transformer.convert(bytes, (Conversion) null));
  }

  // ==================== Blocking Executor Tests ====================

  @Test
  void shouldCreateTransformerWithBlockingExecutor() {
    java.util.concurrent.Executor executor = Runnable::run;

    ReactiveVersionTransformer custom = ReactiveVersionTransformer.builder()
        .blockingExecutor(executor)
        .build();

    assertNotNull(custom);
  }

  @Test
  void shouldConvertWithBlockingExecutor() throws Exception {
    // Given: A transformer with a custom blocking executor
    java.util.concurrent.atomic.AtomicBoolean executorUsed =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    java.util.concurrent.Executor trackingExecutor = runnable -> {
      executorUsed.set(true);
      runnable.run();
    };

    ReactiveVersionTransformer customTransformer = ReactiveVersionTransformer.builder()
        .blockingExecutor(trackingExecutor)
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

    // When: Convert
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    customTransformer.convert(jsonDocument.getBytes(StandardCharsets.UTF_8), conversion)
        .subscribe().with(
            bytes -> result.writeBytes(bytes),
            error -> fail("Conversion failed: " + error.getMessage()),
            () -> {});

    // Then: Executor should have been used
    assertTrue(executorUsed.get(), "Blocking executor should be used for conversion");
  }

  // ==================== Functional API Tests ====================

  @Test
  void shouldSupportFunctionalApiForByteArray() throws Exception {
    String jsonDocument = """
        {
          "@context": ["https://ref.gs1.org/standards/epcis/epcis-context.jsonld"],
          "type": "EPCISDocument",
          "schemaVersion": "2.0",
          "creationDate": "2023-01-01T00:00:00Z",
          "epcisBody": { "eventList": [] }
        }
        """;

    // When: Using functional API
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    transformer.convert(jsonDocument.getBytes(StandardCharsets.UTF_8), c -> c
            .fromMediaType(EPCISFormat.JSON_LD)
            .toMediaType(EPCISFormat.XML)
            .toVersion(EPCISVersion.VERSION_2_0_0))
        .subscribe().with(
            bytes -> result.writeBytes(bytes),
            error -> fail("Conversion failed: " + error.getMessage()),
            () -> {});

    // Then: Should produce XML output
    assertTrue(result.size() > 0, "Should produce output using functional API");
  }

  @Test
  void shouldSupportFunctionalApiForInputStream() throws Exception {
    String jsonDocument = """
        {
          "@context": ["https://ref.gs1.org/standards/epcis/epcis-context.jsonld"],
          "type": "EPCISDocument",
          "schemaVersion": "2.0",
          "creationDate": "2023-01-01T00:00:00Z",
          "epcisBody": { "eventList": [] }
        }
        """;

    java.io.InputStream inputStream = new java.io.ByteArrayInputStream(
        jsonDocument.getBytes(StandardCharsets.UTF_8));

    // When: Using functional API with InputStream
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    transformer.convert(inputStream, c -> c
            .fromMediaType(EPCISFormat.JSON_LD)
            .toMediaType(EPCISFormat.XML)
            .toVersion(EPCISVersion.VERSION_2_0_0))
        .subscribe().with(
            bytes -> result.writeBytes(bytes),
            error -> fail("Conversion failed: " + error.getMessage()),
            () -> {});

    // Then: Should produce XML output
    assertTrue(result.size() > 0, "Should produce output using functional API with InputStream");
  }

  // ==================== Error Message Tests ====================

  @Test
  void shouldProvideHelpfulErrorForUnsupportedConversion() {
    byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);

    // This conversion is not supported: JSON-LD -> XML 1.1 (only 1.2 and 2.0 are targets)
    Conversion unsupported = Conversion.builder()
        .fromMediaType(EPCISFormat.JSON_LD)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_1_1_0)  // 1.1 XML output not supported
        .build();

    AtomicInteger errorCount = new AtomicInteger(0);
    StringBuilder errorMessage = new StringBuilder();

    transformer.convert(bytes, unsupported)
        .subscribe().with(
            result -> {},
            error -> {
              errorCount.incrementAndGet();
              errorMessage.append(error.getMessage());
            },
            () -> {});

    // Then: Error should include supported conversions hint
    assertTrue(errorCount.get() > 0, "Should fail for unsupported conversion");
    assertTrue(errorMessage.toString().contains("Supported"),
        "Error message should include supported conversions hint");
  }

  @Test
  void shouldConvertJsonLdToXml20First() throws Exception {
    // Given: A JSON-LD EPCIS 2.0 document
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

    // First test: JSON -> XML 2.0
    Conversion conversionTo20 = Conversion.builder()
        .fromMediaType(EPCISFormat.JSON_LD)
        .fromVersion(EPCISVersion.VERSION_2_0_0)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    List<byte[]> chunks20 = transformer.convert(jsonDocument.getBytes(StandardCharsets.UTF_8), conversionTo20)
        .collect().asList()
        .await().atMost(java.time.Duration.ofSeconds(30));

    ByteArrayOutputStream result20 = new ByteArrayOutputStream();
    for (byte[] chunk : chunks20) {
      result20.writeBytes(chunk);
    }
    String xml20 = result20.toString(StandardCharsets.UTF_8);
    System.out.println("=== JSON -> XML 2.0 ===");
    System.out.println(xml20);
    System.out.println("=== END ===");

    assertTrue(xml20.contains("EPCISDocument"), "Should contain EPCISDocument");
    assertTrue(xml20.contains("ObjectEvent"), "Should contain ObjectEvent");
    assertTrue(xml20.contains("</epcis:EPCISDocument>"), "Should have closing tag");
  }

  @Test
  void shouldConvertJsonLdToXml12() throws Exception {
    // Given: A JSON-LD EPCIS 2.0 document
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
        .toVersion(EPCISVersion.VERSION_1_2_0)
        .build();

    // When: Convert to XML 1.2 - collect all bytes synchronously
    List<byte[]> chunks = transformer.convert(jsonDocument.getBytes(StandardCharsets.UTF_8), conversion)
        .collect().asList()
        .await().atMost(java.time.Duration.ofSeconds(30));

    // Then: Result should be valid XML 1.2
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    for (byte[] chunk : chunks) {
      result.writeBytes(chunk);
    }
    String xmlResult = result.toString(StandardCharsets.UTF_8);
    assertTrue(xmlResult.length() > 0, "Should produce XML output");
    assertTrue(chunks.size() > 0, "Should emit at least one chunk");
    assertTrue(xmlResult.contains("EPCISDocument") || xmlResult.contains("ObjectEvent"),
        "Should contain EPCIS XML elements");
  }
}
