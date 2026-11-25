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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    assertTrue(xml.contains("urn:epcglobal:epcis-query:xsd:1"), xml);
    assertTrue(xml.contains("schemaVersion=\"1.2\""), xml);
    assertTrue(xml.contains("QueryResults"), xml);
    assertTrue(xml.contains("<queryName>SimpleEventQuery</queryName>"), xml);
    assertTrue(xml.contains("<resultsBody>"), xml);
    assertTrue(xml.contains("<ObjectEvent>"), xml);
    assertTrue(xml.contains("urn:epc:id:sgtin:0614141.107346.2017"), xml);
    assertFalse(xml.contains("xsd:2"), xml);
    // persistentDisposition is an EPCIS 2.0 element; the 1.2 document carries it inside extension
    assertTrue(xml.contains("<extension>"), xml);
    assertTrue(xml.indexOf("<extension>") < xml.indexOf("<persistentDisposition>"), xml);
  }

  @Test
  @DisplayName("a query document without events still comes back as a complete 1.2 document")
  void emptyQueryDocumentIsConverted() throws Exception {
    final String empty =
        QUERY_DOCUMENT_2_0.replaceAll("(?s)<EventList>.*</EventList>", "<EventList/>");
    final String xml = convertTo12(empty);
    assertTrue(xml.contains("urn:epcglobal:epcis-query:xsd:1"), xml);
    assertTrue(xml.contains("<queryName>SimpleEventQuery</queryName>"), xml);
    assertTrue(xml.contains("EventList"), xml);
    assertFalse(xml.contains("ObjectEvent"), xml);
  }

  @Test
  @DisplayName("a JSON EPCISQueryDocument keeps queryName and subscriptionID converted to XML 1.2")
  void jsonQueryDocumentKeepsQueryNameIn12() throws Exception {
    final String xml = convertJson("queryDocument.json", EPCISVersion.VERSION_1_2_0);
    assertTrue(xml.contains("urn:epcglobal:epcis-query:xsd:1"), xml);
    assertTrue(xml.contains("<queryName>SimpleEventQuery</queryName>"), xml);
    assertTrue(
        xml.contains("<subscriptionID>32d2aec1-a6d2-46d9-900a-24124288cce1</subscriptionID>"), xml);
  }

  @Test
  @DisplayName("a JSON EPCISQueryDocument keeps queryName and subscriptionID converted to XML 2.0")
  void jsonQueryDocumentKeepsQueryNameIn20() throws Exception {
    final String xml = convertJson("queryDocument.json", EPCISVersion.VERSION_2_0_0);
    assertTrue(xml.contains("urn:epcglobal:epcis-query:xsd:2"), xml);
    assertTrue(xml.contains("<queryName>SimpleEventQuery</queryName>"), xml);
    assertTrue(
        xml.contains("<subscriptionID>32d2aec1-a6d2-46d9-900a-24124288cce1</subscriptionID>"), xml);
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
    final int queryName = xml.indexOf("<queryName>SimpleEventQuery</queryName>");
    final int subscriptionID =
        xml.indexOf("<subscriptionID>32d2aec1-a6d2-46d9-900a-24124288cce1</subscriptionID>");
    final int resultsBody = xml.indexOf("<resultsBody>");
    assertTrue(queryName >= 0 && subscriptionID >= 0 && resultsBody >= 0, xml);
    assertTrue(queryName < subscriptionID, xml);
    assertTrue(subscriptionID < resultsBody, xml);
    // QueryResults is a global element of the query schema: epcisq:QueryResults in the query
    // namespace of the target version, never unqualified
    final org.w3c.dom.Document dom = parseNamespaceAware(xml);
    final org.w3c.dom.Element results =
        (org.w3c.dom.Element) dom.getElementsByTagNameNS("*", "QueryResults").item(0);
    assertTrue(results != null, xml);
    org.junit.jupiter.api.Assertions.assertEquals(
        "1.2".equals(toVersion) ? "urn:epcglobal:epcis-query:xsd:1" : "urn:epcglobal:epcis-query:xsd:2",
        results.getNamespaceURI(),
        xml);
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
