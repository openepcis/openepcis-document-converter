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
package io.openepcis.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * A query interface answers with an EPCISQueryDocument, and a client asking for EPCIS 1.2 expects
 * that document in the 1.2 query namespace with its events converted, not a copy of the 2.0 one.
 */
class QueryDocumentVersion12Test {

  private static final String QUERY_DOCUMENT_2_0 =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <epcisq:EPCISQueryDocument xmlns:epcisq="urn:epcglobal:epcis-query:xsd:2"
          xmlns:epcis="urn:epcglobal:epcis:xsd:2" schemaVersion="2.0"
          creationDate="2026-10-21T09:00:00.000Z">
        <EPCISBody>
          <epcisq:QueryResults>
            <queryName>SimpleEventQuery</queryName>
            <resultsBody>
              <EventList>
                <ObjectEvent>
                  <eventTime>2026-10-21T08:00:00.000+01:00</eventTime>
                  <eventTimeZoneOffset>+01:00</eventTimeZoneOffset>
                  <epcList>
                    <epc>urn:epc:id:sgtin:0614141.107346.2017</epc>
                  </epcList>
                  <action>OBSERVE</action>
                  <bizStep>shipping</bizStep>
                  <persistentDisposition>
                    <set>completeness_verified</set>
                  </persistentDisposition>
                </ObjectEvent>
              </EventList>
            </resultsBody>
          </epcisq:QueryResults>
        </EPCISBody>
      </epcisq:EPCISQueryDocument>
      """;

  @Test
  @DisplayName("an EPCISQueryDocument 2.0 becomes an EPCISQueryDocument 1.2 with converted events")
  void queryDocumentIsConverted() throws Exception {
    final String xml = convertTo12(QUERY_DOCUMENT_2_0);
    validate(xml, "1.2");
    final Document dom = parseNamespaceAware(xml);
    assertEquals("1.2", dom.getDocumentElement().getAttribute("schemaVersion"), xml);
    assertEquals(List.of("queryName", "resultsBody"), childNames(queryResults(dom, "1.2")), xml);
    assertEquals("SimpleEventQuery", text(dom, "queryName"), xml);
    assertEquals("urn:epc:id:sgtin:0614141.107346.2017", text(dom, "epc"), xml);
    assertFalse(xml.contains("xsd:2"), xml);
    // persistentDisposition is an EPCIS 2.0 element; the 1.2 document carries it inside extension
    final Node persistentDisposition = dom.getElementsByTagNameNS("*", "persistentDisposition").item(0);
    assertTrue(persistentDisposition != null, xml);
    assertEquals("extension", persistentDisposition.getParentNode().getLocalName(), xml);
  }

  @Test
  @DisplayName("a query document without events still comes back as a complete 1.2 document")
  void emptyQueryDocumentIsConverted() throws Exception {
    final String empty =
        QUERY_DOCUMENT_2_0.replaceAll("(?s)<EventList>.*</EventList>", "<EventList/>");
    final String xml = convertTo12(empty);
    validate(xml, "1.2");
    final Document dom = parseNamespaceAware(xml);
    assertEquals("SimpleEventQuery", text(dom, "queryName"), xml);
    assertEquals(1, dom.getElementsByTagNameNS("*", "EventList").getLength(), xml);
    assertFalse(xml.contains("ObjectEvent"), xml);
  }

  @Test
  @DisplayName("a JSON EPCISQueryDocument keeps queryName and subscriptionID converted to XML 1.2")
  void jsonQueryDocumentKeepsQueryNameIn12() throws Exception {
    final String xml = convertJson("queryDocument.json", EPCISVersion.VERSION_1_2_0);
    validate(xml, "1.2");
    final Document dom = parseNamespaceAware(xml);
    assertEquals("SimpleEventQuery", text(dom, "queryName"), xml);
    assertEquals("32d2aec1-a6d2-46d9-900a-24124288cce1", text(dom, "subscriptionID"), xml);
  }

  @Test
  @DisplayName("a JSON EPCISQueryDocument keeps queryName and subscriptionID converted to XML 2.0")
  void jsonQueryDocumentKeepsQueryNameIn20() throws Exception {
    final String xml = convertJson("queryDocument.json", EPCISVersion.VERSION_2_0_0);
    validate(xml, "2.0");
    final Document dom = parseNamespaceAware(xml);
    assertEquals("SimpleEventQuery", text(dom, "queryName"), xml);
    assertEquals("32d2aec1-a6d2-46d9-900a-24124288cce1", text(dom, "subscriptionID"), xml);
  }

  @ParameterizedTest(name = "{0} {1} to XML {2}")
  @CsvSource({
    "JSON_LD, queryDocument.json, 1.2",
    "JSON_LD, queryDocument.json, 2.0",
    "XML, queryDocument.xml, 1.2",
    "XML, queryDocument.xml, 2.0"
  })
  @DisplayName("QueryResults children come in schema order: queryName, subscriptionID, resultsBody")
  void queryResultsChildrenInSchemaOrder(
      final String fromFormat, final String name, final String toVersion) throws Exception {
    final EPCISFormat from = EPCISFormat.valueOf(fromFormat);
    final String folder = from == EPCISFormat.XML ? "XML" : "JSON";
    final String xml;
    try (InputStream in =
            QueryDocumentVersion12Test.class
                .getClassLoader()
                .getResourceAsStream("2.0/EPCIS/" + folder + "/Query/" + name);
        InputStream converted =
            new VersionTransformer()
                .convert(
                    in,
                    Conversion.of(
                        from,
                        EPCISVersion.VERSION_2_0_0,
                        EPCISFormat.XML,
                        "1.2".equals(toVersion)
                            ? EPCISVersion.VERSION_1_2_0
                            : EPCISVersion.VERSION_2_0_0))) {
      xml = new String(converted.readAllBytes(), StandardCharsets.UTF_8);
    }
    // The XSD fixes the order (queryName, subscriptionID, resultsBody) and that QueryResults is
    // the global element epcisq:QueryResults of the target version's query namespace
    validate(xml, toVersion);
    final Document dom = parseNamespaceAware(xml);
    assertEquals(
        List.of("queryName", "subscriptionID", "resultsBody"),
        childNames(queryResults(dom, toVersion)),
        xml);
    assertEquals("SimpleEventQuery", text(dom, "queryName"), xml);
    assertEquals("32d2aec1-a6d2-46d9-900a-24124288cce1", text(dom, "subscriptionID"), xml);
  }

  // The official query schemas: 2.0 copied from openepcis-document-validation-service, 1.2 the set
  // the XSLT conversion ships with.
  private static Schema schema(final String version) throws Exception {
    final String path =
        "1.2".equals(version)
            ? "xalan-conversion/1.2/XSD/EPCglobal-epcis-query-1_2.xsd"
            : "xsd/2_0_0/EPCglobal-epcis-query-2_0.xsd";
    final java.net.URL xsd = QueryDocumentVersion12Test.class.getClassLoader().getResource(path);
    assertTrue(xsd != null, path);
    return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(xsd);
  }

  private static void validate(final String xml, final String version) throws Exception {
    try {
      schema(version).newValidator().validate(new StreamSource(new StringReader(xml)));
    } catch (final org.xml.sax.SAXException invalid) {
      throw new AssertionError("not valid against the " + version + " query XSD: " + invalid.getMessage() + "\n" + xml, invalid);
    }
  }

  private static Element queryResults(final Document dom, final String version) {
    return (Element)
        dom.getElementsByTagNameNS(
                "1.2".equals(version)
                    ? "urn:epcglobal:epcis-query:xsd:1"
                    : "urn:epcglobal:epcis-query:xsd:2",
                "QueryResults")
            .item(0);
  }

  private static List<String> childNames(final Element parent) {
    final List<String> names = new ArrayList<>();
    for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element element) {
        names.add(element.getLocalName());
      }
    }
    return names;
  }

  private static String text(final Document dom, final String localName) {
    final Node node = dom.getElementsByTagNameNS("*", localName).item(0);
    return node == null ? null : node.getTextContent().trim();
  }

  private static org.w3c.dom.Document parseNamespaceAware(final String xml) throws Exception {
    final javax.xml.parsers.DocumentBuilderFactory factory =
        javax.xml.parsers.DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    return factory
        .newDocumentBuilder()
        .parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  // A reference query document from openepcis-test-resources, JSON-LD 2.0 to XML.
  private static String convertJson(final String name, final EPCISVersion toVersion)
      throws Exception {
    try (InputStream in =
            QueryDocumentVersion12Test.class
                .getClassLoader()
                .getResourceAsStream("2.0/EPCIS/JSON/Query/" + name);
        InputStream converted =
            new VersionTransformer()
                .convert(
                    in,
                    Conversion.of(
                        EPCISFormat.JSON_LD,
                        EPCISVersion.VERSION_2_0_0,
                        EPCISFormat.XML,
                        toVersion))) {
      return new String(converted.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String convertTo12(final String document) throws Exception {
    final VersionTransformer transformer = new VersionTransformer();
    try (InputStream converted =
        transformer.convert(
            new ByteArrayInputStream(document.getBytes(StandardCharsets.UTF_8)),
            Conversion.of(
                EPCISFormat.XML,
                EPCISVersion.VERSION_2_0_0,
                EPCISFormat.XML,
                EPCISVersion.VERSION_1_2_0))) {
      return new String(converted.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
