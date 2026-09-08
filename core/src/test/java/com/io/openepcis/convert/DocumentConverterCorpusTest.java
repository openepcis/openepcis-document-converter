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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.constants.EPCIS;
import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.Conversion;
import io.openepcis.converter.VersionTransformer;
import io.openepcis.converter.collector.EventHandler;
import io.openepcis.converter.collector.JsonEPCISEventCollector;
import io.openepcis.converter.collector.XmlEPCISEventCollector;
import io.openepcis.converter.json.JsonToXmlConverter;
import io.openepcis.converter.xml.XmlToJsonConverter;
import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class DocumentConverterCorpusTest {
    private static final String RESOURCE_INDEX = "openepcis-test-resources.list";

    private static final String XML_DOCS = "2.0/EPCIS/XML/Capture/Documents/";
    private static final String XML_EVENTS = "2.0/EPCIS/XML/Capture/Events/";
    private static final String XML_QUERY = "2.0/EPCIS/XML/Query/";
    private static final String XML_12_DOCS = "1.2/EPCIS/XML/Capture/Documents/";

    private static final String JSON_DOCS = "2.0/EPCIS/JSON/Capture/Documents/";
    private static final String JSON_EVENTS = "2.0/EPCIS/JSON/Capture/Events/";
    private static final String JSON_QUERY = "2.0/EPCIS/JSON/Query/";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final XMLInputFactory XML_INPUT = XMLInputFactory.newInstance();

    static {
        // No DTD needed so disabled it
        XML_INPUT.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    }

    private final VersionTransformer versionTransformer;

    DocumentConverterCorpusTest() throws JAXBException {
        this.versionTransformer = new VersionTransformer();
    }

    static Stream<String> xmlDocuments() throws IOException {
        return corpus(".xml", XML_DOCS, XML_QUERY);
    }

    static Stream<String> jsonDocuments() throws IOException {
        return corpus(".json", JSON_DOCS, JSON_QUERY);
    }

    static Stream<String> xmlSingleEvents() throws IOException {
        return corpus(".xml", XML_EVENTS);
    }

    static Stream<String> jsonSingleEvents() throws IOException {
        return corpus(".json", JSON_EVENTS);
    }

    static Stream<String> xml12Documents() throws IOException {
        return corpus(".xml", XML_12_DOCS);
    }

    // Conversion using EventHandler for the XML -> JSON/JSON-LD
    @ParameterizedTest(name = "{0}")
    @MethodSource("xmlDocuments")
    void handlerKeepsEveryEventFromXmlToJson(final String path) {
        final byte[] source = read(path);
        final byte[] converted = xmlToJsonEventHandler(path, source);
        final int expected = countEvents(path, source, true);

        assertTrue(expected > 0, path + ": the source document holds no events");
        assertEquals(expected, countEvents(path, converted, false), path + ": event count changed converting XML to JSON");
    }

    // Conversion using EventHandler for the JSON/JSON-LD -> XML
    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonDocuments")
    void handlerKeepsEveryEventFromJsonToXml(final String path) {
        final byte[] source = read(path);
        final byte[] converted = jsonToXmlEventHandler(path, source);
        final int expected = countEvents(path, source, false);

        assertTrue(expected > 0, path + ": the source document holds no events");
        assertEquals(expected, countEvents(path, converted, true), path + ": event count changed converting JSON to XML");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("xmlDocuments")
    void transformerKeepsEveryEventFromXmlToJson(final String path) {
        final byte[] source = read(path);
        final byte[] converted = transform(path, source, EPCISFormat.XML, EPCISFormat.JSON_LD, EPCISVersion.VERSION_2_0_0);

        assertEquals(countEvents(path, source, true), countEvents(path, converted, false), path + ": event count changed on the VersionTransformer XML to JSON path");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonDocuments")
    void transformerKeepsEveryEventFromJsonToXml(final String path) {
        final byte[] source = read(path);
        final byte[] converted = transform(path, source, EPCISFormat.JSON_LD, EPCISFormat.XML, EPCISVersion.VERSION_2_0_0);

        assertEquals(countEvents(path, source, false), countEvents(path, converted, true), path + ": event count changed on the VersionTransformer JSON to XML path");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("xml12Documents")
    void transformerUpgradesEveryDocumentFrom12To20(final String path) {
        final byte[] source = read(path);
        final byte[] upgraded;
        final Conversion cb = Conversion.builder()
                .generateGS1CompliantDocument(false)
                .fromMediaType(EPCISFormat.XML)
                .fromVersion(EPCISVersion.VERSION_1_2_0)
                .toVersion(EPCISVersion.VERSION_2_0_0)
                .build();

        try (InputStream in = new ByteArrayInputStream(source);
             InputStream out = versionTransformer.convert(in, cb)) {
            upgraded = out.readAllBytes();
        } catch (IOException e) {
            fail(path + ": 1.2 to 2.0 upgrade threw", e);
            return;
        }

        assertEquals(countEvents(path, source, true), countEvents(path, upgraded, true), path + ": event count changed upgrading 1.2 to 2.0");
    }


    @ParameterizedTest(name = "{0}")
    @MethodSource("xmlDocuments")
    void xmlSurvivesARoundTrip(final String path) {
        final byte[] source = read(path);
        final byte[] json = transform(path, source, EPCISFormat.XML, EPCISFormat.JSON_LD, EPCISVersion.VERSION_2_0_0);
        final byte[] back = transform(path, json, EPCISFormat.JSON_LD, EPCISFormat.XML, EPCISVersion.VERSION_2_0_0);

        assertEquals(countEvents(path, source, true), countEvents(path, back, true), path + ": event count changed over XML to JSON to XML");
    }


    // Test bare event conversion from XML -> JSON
    @ParameterizedTest(name = "{0}")
    @MethodSource("xmlSingleEvents")
    void standaloneXmlEventConvertsToJson(final String path) {
        final byte[] source = read(path);
        final byte[] converted = transform(path, source, EPCISFormat.XML, EPCISFormat.JSON_LD, EPCISVersion.VERSION_2_0_0);

        assertEquals(1, countEvents(path, converted, false), path + ": expected exactly one event");
    }

    // Test bare event conversion from JSON -> XML
    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonSingleEvents")
    void standaloneJsonEventConvertsToXml(final String path) {
        final byte[] source = read(path);
        final byte[] converted = transform(path, source, EPCISFormat.JSON_LD, EPCISFormat.XML, EPCISVersion.VERSION_2_0_0);

        assertEquals(1, countEvents(path, converted, true), path + ": expected exactly one event");
    }

    // Roundtrip test to convert json to xml and then back to json
    @ParameterizedTest(name = "{0}")
    @MethodSource("xmlSingleEvents")
    void standaloneEventSurvivesARoundTrip(final String path) {
        final byte[] source = read(path);
        final byte[] json = transform(path, source, EPCISFormat.XML, EPCISFormat.JSON_LD, EPCISVersion.VERSION_2_0_0);
        final byte[] back = transform(path, json, EPCISFormat.JSON_LD, EPCISFormat.XML, EPCISVersion.VERSION_2_0_0);

        assertEquals(1, countEvents(path, back, true), path + ": expected exactly one event after the round trip");
    }

    private static Stream<String> corpus(final String extension, final String... directories) throws IOException {
        final List<String> index = readResourceIndex();
        final Set<String> found = new TreeSet<>();

        for (final String directory : directories) {
            for (final String line : index) {
                final String path = line.startsWith("/") ? line.substring(1) : line;

                // Direct children only, which leaves out Invalid/ and Queries/ subfolders
                if (path.startsWith(directory) && path.endsWith(extension) && !path.substring(directory.length()).contains("/")) {
                    found.add(path);
                }
            }
        }

        assertTrue(!found.isEmpty(), "no documents found under " + String.join(", ", directories));
        return found.stream();
    }

    private static List<String> readResourceIndex() throws IOException {
        try (InputStream in = DocumentConverterCorpusTest.class.getClassLoader().getResourceAsStream(RESOURCE_INDEX)) {
            assertNotNull(in, "resource index missing from the classpath: " + RESOURCE_INDEX);

            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();
        }
    }

    private static byte[] read(final String path) {
        try (InputStream in = DocumentConverterCorpusTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "document missing from the classpath: " + path);
            return in.readAllBytes();
        } catch (IOException e) {
            return fail(path + ": could not be read", e);
        }
    }

    // Convert the XML to JSON for provided document/event using the EventHandler
    private static byte[] xmlToJsonEventHandler(final String path, final byte[] document) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (EventHandler handler = new EventHandler(new JsonEPCISEventCollector(out))) {
            new XmlToJsonConverter().convert(new ByteArrayInputStream(document), handler);
        } catch (Exception e) {
            return fail(path + ": XmlToJsonConverter threw", e);
        }
        return out.toByteArray();
    }

    // Convert the JSON to XML for provided document/event using the EventHandler
    private static byte[] jsonToXmlEventHandler(final String path, final byte[] document) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (EventHandler handler = new EventHandler(new XmlEPCISEventCollector(out))) {
            new JsonToXmlConverter().convert(new ByteArrayInputStream(document), handler);
        } catch (Exception e) {
            return fail(path + ": JsonToXmlConverter threw", e);
        }
        return out.toByteArray();
    }

    // Using Conversion to transform XML <-> JSON for provided document/event
    private byte[] transform(String path, byte[] document, EPCISFormat from, EPCISFormat to, EPCISVersion toVersion) {
        final Conversion cb = Conversion.builder().fromMediaType(from).toMediaType(to).toVersion(toVersion).build();
        try (InputStream in = new ByteArrayInputStream(document);
             InputStream out = versionTransformer.convert(in, cb)) {
            return out.readAllBytes();
        } catch (Exception e) {
            return fail(path + ": VersionTransformer " + from + " to " + to + " threw", e);
        }
    }

    private static int countEvents(final String path, final byte[] document, final boolean xml) {
        if (document.length == 0) {
            return fail(path + ": the conversion produced an empty document");
        }

        try {
            return xml ? countXmlEvents(document) : countJsonEvents(MAPPER.readTree(document));
        } catch (Exception e) {
            return fail(path + ": could not read the document, it starts with\n" + snippet(document), e);
        }
    }

    private static int countXmlEvents(final byte[] document) throws Exception {
        final XMLStreamReader reader = XML_INPUT.createXMLStreamReader(new ByteArrayInputStream(document));
        int count = 0;
        try {
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT && EPCIS.EPCIS_EVENT_TYPES.contains(reader.getLocalName())) {
                    count++;
                }
            }
        } finally {
            reader.close();
        }
        return count;
    }

    private static int countJsonEvents(final JsonNode node) {
        int count = 0;
        final JsonNode type = node.get("type");

        // Only an event carries one of the five event type names, nested "type" fields hold CBV values
        if (node.isObject() && type != null && type.isTextual() && EPCIS.EPCIS_EVENT_TYPES.contains(type.asText())) {
            count++;
        }
        for (final JsonNode child : node) {
            count += countJsonEvents(child);
        }
        return count;
    }

    private static String snippet(final byte[] document) {
        final int length = Math.min(document.length, 300);
        return new String(document, 0, length, StandardCharsets.UTF_8);
    }
}
