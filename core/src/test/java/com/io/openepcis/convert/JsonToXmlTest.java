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
import io.openepcis.converter.collector.XmlEPCISEventCollector;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.converter.json.JsonToXmlConverter;
import io.openepcis.converter.util.XMLFormatter;
import io.openepcis.converter.validator.EventValidator;
import jakarta.xml.bind.JAXBException;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class JsonToXmlTest {

    private final VersionTransformer versionTransformer;

    public JsonToXmlTest() throws JAXBException {
        this.versionTransformer = new VersionTransformer();
    }

    final XMLFormatter formatter = new XMLFormatter();
    final Conversion conversionBuilder = Conversion.builder().fromMediaType(EPCISFormat.JSON_LD).toMediaType(EPCISFormat.XML).toVersion(EPCISVersion.VERSION_2_0_0).build();

    @Test
    void jsonToXmlObjectEventTest() throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("2.0/EPCIS/JSON/Capture/Documents/ObjectEvent_all_possible_fields.json");
        try (final EventHandler handler =
                     new EventHandler(new EventValidator(), new XmlEPCISEventCollector(byteArrayOutputStream))) {
            new JsonToXmlConverter().convert(inputStream, handler);
            assertTrue(byteArrayOutputStream.toString().length() > 0);
        }
    }

    @Test
    void invalidData() throws Exception {
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Consumer<Throwable> failureConsumer = failure::set;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        InputStream inputStream = new ByteArrayInputStream("noop".getBytes(StandardCharsets.UTF_8));
        try (final EventHandler handler =
                     new EventHandler(new EventValidator(), new XmlEPCISEventCollector(byteArrayOutputStream))) {
            assertThrows(FormatConverterException.class, () -> {
                new JsonToXmlConverter().convert(inputStream, handler.onFailure(failureConsumer));
            });
            Assertions.assertNotNull(failure.get());
        }
    }

    //
    // Unusable input Invalid document without type to confirm nothing gets blocked and  must return promptly. Must never kept hanging
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void jsonWithoutTypeDoesNotSpin() throws Exception {
        // No type and no eventID, so the header scan never meets its stop token
        final String noType = "{ \"foo\" : \"bar\" }";

        final byte[] result;
        try (InputStream in = new ByteArrayInputStream(noType.getBytes(StandardCharsets.UTF_8));
             InputStream out = new VersionTransformer().convert(in, conversionBuilder)) {
            result = out.readAllBytes();
        }

        // Unusable input yields an empty event list rather than an error, which is the current contract
        assertTrue(new String(result, StandardCharsets.UTF_8).contains("<EventList></EventList>"));
    }

    @Test
    void JsonToXmlWithNamespacesDocumentAndEventTest() throws Exception {
        final InputStream inputStream = getClass().getClassLoader().getResourceAsStream("2.0/EPCIS/JSON/Capture/Documents/Namespaces_document_and_event.json");
        final InputStream convertedDocument = versionTransformer.convert(inputStream, conversionBuilder);

        final String outputXml = IOUtils.toString(convertedDocument, StandardCharsets.UTF_8);
        assertNotNull(outputXml);
        //System.out.println(outputXml);
    }

    @Test
    void JsonToXmlWithBareEventNamespacesTest() throws Exception {
        final InputStream inputStream = getClass().getClassLoader().getResourceAsStream("2.0/EPCIS/JSON/Capture/Events/Namespaces_bare_event.json");
        final InputStream convertedDocument = versionTransformer.convert(inputStream, conversionBuilder);

        final String outputXml = IOUtils.toString(convertedDocument, StandardCharsets.UTF_8);
        assertNotNull(outputXml);
        //System.out.println(outputXml);
    }

    @Test
    void JsonToXmlWithNamespacesAtDifferentLevel() throws Exception {
        final InputStream inputStream = getClass().getClassLoader().getResourceAsStream("2.0/EPCIS/JSON/Capture/Documents/Namespaces_at_different_level.json");
        final InputStream convertedDocument = versionTransformer.convert(inputStream, conversionBuilder);

        final String outputXml = IOUtils.toString(convertedDocument, StandardCharsets.UTF_8);
        assertNotNull(outputXml);
        //System.out.println(outputXml);
    }
}
