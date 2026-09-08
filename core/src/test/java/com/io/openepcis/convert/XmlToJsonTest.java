/*
 * Copyright 2022-2026 benelog GmbH & Co. KG
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
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.converter.validator.EventValidator;
import io.openepcis.converter.xml.XmlToJsonConverter;
import jakarta.xml.bind.JAXBException;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class XmlToJsonTest {
    private final VersionTransformer versionTransformer;
    private final Conversion conversionBuilder = Conversion.builder()
            .generateGS1CompliantDocument(false)
            .fromMediaType(EPCISFormat.XML)
            .toMediaType(EPCISFormat.JSON_LD)
            .toVersion(EPCISVersion.VERSION_2_0_0).build();

    public XmlToJsonTest() throws JAXBException {
        this.versionTransformer = new VersionTransformer();
    }

    // Test case for Invalid values for EventHandler
    @Test
    void invalidDocumentTest() {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("2.0/EPCIS/JSON/Capture/Documents/TransformationEvent_all_possible_fields.json");
        assertThrows(FormatConverterException.class, () -> {
            try (final EventHandler handler = new EventHandler(null, null)) {
                new XmlToJsonConverter().convert(inputStream, handler);
            }
        });
    }

    // Test case for Invalid JSON file contents
    @Test
    void fileNotPresentTest() throws Exception {
        final InputStream inputStream = getClass().getClassLoader().getResourceAsStream("2.0/EPCIS/JSON/Capture/Documents/file_does_not_exist.xml");
        final XmlToJsonConverter converter = new XmlToJsonConverter();

        try (final EventHandler handler = new EventHandler(new EventValidator(), null)) {
            assertThrows(FormatConverterException.class, () -> converter.convert(inputStream, handler));
        }
    }

    // Test to only validate the converted JSON events against JSON-Schema
    @Test
    void validationTest() throws Exception {
        final InputStream inputStream = getClass().getClassLoader().getResourceAsStream("2.0/EPCIS/XML/Capture/Documents/ObjectEvent.xml");
        try (EventHandler eventHandler = new EventHandler(new EventValidator(), null)) {
            assertDoesNotThrow(() -> new XmlToJsonConverter().convert(inputStream, eventHandler));
        }
    }

    @Test
    void document12WithErrorDeclarationTest() throws Exception {
        final InputStream inputStream = getClass().getClassLoader().getResourceAsStream("1.2/EPCIS/XML/Capture/Documents/ObjectEvent_with_baseExtension_errorDeclaration.xml");
        final InputStream convertedDocument = versionTransformer.convert(inputStream, conversionBuilder);

        assertFalse(IOUtils.toString(convertedDocument, StandardCharsets.UTF_8).isEmpty());
    }

    /*
       Tests for EPCISQueryDocument conversion from JSON to XML
    */
    @Test
    void invalidData() throws Exception {
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Consumer<Throwable> failureConsumer = failure::set;
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final InputStream inputStream = new ByteArrayInputStream("noop".getBytes(StandardCharsets.UTF_8));

        try (final EventHandler handler = new EventHandler(new EventValidator(), new JsonEPCISEventCollector(byteArrayOutputStream))) {
            Assertions.assertThrows(FormatConverterException.class, () -> {
                new XmlToJsonConverter().convert(inputStream, handler.onFailure(failureConsumer));
            });
            Assertions.assertNotNull(failure.get());
        }
    }


    @Test
    void XmlToJsonWithNamespacesDocumentAndEventTest() throws Exception {
        final InputStream inputStream = getClass().getClassLoader().getResourceAsStream("2.0/EPCIS/XML/Capture/Documents/Namespaces_document_and_event.xml");
        final InputStream convertedDocument = versionTransformer.convert(inputStream, conversionBuilder);

        final String outputJson = IOUtils.toString(convertedDocument, StandardCharsets.UTF_8);
        assertNotNull(outputJson);
        //System.out.println(outputJson);
    }

    @Test
    void XmlToJsonWithBareEventNamespacesTest() throws Exception {
        final InputStream inputStream = getClass().getClassLoader().getResourceAsStream("2.0/EPCIS/XML/Capture/Events/Namespaces_bare_event.xml");
        final InputStream convertedDocument = versionTransformer.convert(inputStream, conversionBuilder);

        final String outputJson = IOUtils.toString(convertedDocument, StandardCharsets.UTF_8);
        assertNotNull(outputJson);
        //System.out.println(outputJson);
    }

    @Test
    void XmlToJsonWithNamespacesAtDifferentLevel() throws Exception {
        final InputStream inputStream = getClass().getClassLoader().getResourceAsStream("2.0/EPCIS/XML/Capture/Documents/Namespaces_at_different_level.xml");
        final InputStream convertedDocument = versionTransformer.convert(inputStream, conversionBuilder);

        final String outputJson = IOUtils.toString(convertedDocument, StandardCharsets.UTF_8);
        assertNotNull(outputJson);
        //System.out.println(outputJson);
    }
}
