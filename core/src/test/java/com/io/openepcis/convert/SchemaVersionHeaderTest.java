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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.constants.EPCIS;
import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.Conversion;
import io.openepcis.converter.VersionTransformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

// The converted schemaVersion depends only on the target: XML 2.0 "2.0", XML 1.2 "1.2", JSON "2.0.1"
class SchemaVersionHeaderTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern XML_SCHEMA_VERSION = Pattern.compile("schemaVersion=\"([^\"]*)\"");

    private final VersionTransformer versionTransformer;
    private final String EPCIS_DOCUMENT_JSON;
    private final String EPCIS_DOCUMENT_XML;
    private final String EPCIS_EVENT_JSON;

    SchemaVersionHeaderTest() throws Exception {
        this.versionTransformer = new VersionTransformer();
        EPCIS_DOCUMENT_JSON = loadResource("JSON/jsonDocumentWithVariableSchema.json");
        EPCIS_DOCUMENT_XML = loadResource("XML/xmlDocumentWithVariableSchema.xml");
        EPCIS_EVENT_JSON = loadResource("JSON/jsonEventWithVariableSchema.json");
    }

    // JSON documents to XML 2.0
    @ParameterizedTest(name = "JSON schemaVersion {0} to XML 2.0")
    @ValueSource(strings = {"2.0", "2.0.1"})
    void jsonToXml20WritesDecimalSchemaVersion(final String jsonSchemaVersion) throws Exception {
        final String xml = convert(EPCIS_DOCUMENT_JSON.formatted(jsonSchemaVersion), EPCISFormat.JSON_LD, EPCISFormat.XML, EPCISVersion.VERSION_2_0_0);
        assertEquals("2.0", xmlSchemaVersion(xml));
    }

    // JSON documents to XML 1.2
    @ParameterizedTest(name = "JSON schemaVersion {0} to XML 1.2")
    @ValueSource(strings = {"2.0", "2.0.1"})
    void jsonToXml12WritesDecimalSchemaVersion(final String jsonSchemaVersion) throws Exception {
        final String xml = convert(EPCIS_DOCUMENT_JSON.formatted(jsonSchemaVersion), EPCISFormat.JSON_LD, EPCISFormat.XML, EPCISVersion.VERSION_1_2_0);
        assertEquals("1.2", xmlSchemaVersion(xml));
    }

    // XML 2.0 and XML 1.2 documents to JSON always give the latest JSON schemaVersion
    @ParameterizedTest(name = "XML {0} to JSON")
    @ValueSource(strings = {"2.0", "1.2"})
    void xmlToJsonWritesLatestSchemaVersion(final String xmlSchemaVersion) throws Exception {
        final String namespaceVersion = "2.0".equals(xmlSchemaVersion) ? "2" : "1";
        final String json = convert(EPCIS_DOCUMENT_XML.formatted(namespaceVersion, xmlSchemaVersion), EPCISFormat.XML, EPCISFormat.JSON_LD, EPCISVersion.VERSION_2_0_0);
        assertEquals("2.0.1", MAPPER.readTree(json).get(EPCIS.SCHEMA_VERSION).asText());
    }

    // A bare JSON event has no header, converting it to XML must still work
    @ParameterizedTest(name = "JSON event to XML {0}")
    @ValueSource(strings = {"1.2", "2.0"})
    void bareJsonEventConvertsToXml(final String xmlSchemaVersion) throws Exception {
        final EPCISVersion targetVersion = "1.2".equals(xmlSchemaVersion) ? EPCISVersion.VERSION_1_2_0 : EPCISVersion.VERSION_2_0_0;
        final String xml = convert(EPCIS_EVENT_JSON, EPCISFormat.JSON_LD, EPCISFormat.XML, targetVersion);
        assertTrue(xml.contains("ObjectEvent"), "bare JSON event should give an XML ObjectEvent");
    }

    // A bare XML event has no document wrapper, converting it to JSON must still work
    @Test
    void bareXmlEventConvertsToJson() throws Exception {
        final String bareXmlEvent = loadResource("2.0/EPCIS/XML/Capture/Events/ObjectEvent.xml");
        final String json = convert(bareXmlEvent, EPCISFormat.XML, EPCISFormat.JSON_LD, EPCISVersion.VERSION_2_0_0);
        assertEquals("ObjectEvent", MAPPER.readTree(json).get(EPCIS.TYPE).asText());
    }

    private String convert(final String content, final EPCISFormat from, final EPCISFormat to, final EPCISVersion toVersion) throws Exception {
        final Conversion conversion = Conversion.builder().fromMediaType(from).toMediaType(to).toVersion(toVersion).build();
        try (InputStream result = versionTransformer.convert(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), conversion)) {
            return new String(result.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // First schemaVersion attribute in the XML, which is the one on the document root
    private static String xmlSchemaVersion(final String xml) {
        final Matcher matcher = XML_SCHEMA_VERSION.matcher(xml);
        assertTrue(matcher.find(), "No schemaVersion attribute found in XML: " + xml);
        return matcher.group(1);
    }


    private static String loadResource(final String path) throws Exception {
        try (InputStream in = SchemaVersionHeaderTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "document missing from the classpath: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
