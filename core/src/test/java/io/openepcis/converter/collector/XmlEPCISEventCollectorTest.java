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
package io.openepcis.converter.collector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class XmlEPCISEventCollectorTest {

  @Test
  @DisplayName("QueryResults children come in schema order: queryName, subscriptionID, resultsBody")
  void queryResultsChildrenInSchemaOrder() throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final XmlEPCISEventCollector collector = new XmlEPCISEventCollector(out);
    collector.setIsEPCISDocument(false);
    collector.setSubscriptionID("sub-1");
    collector.setQueryName("SimpleEventQuery");
    collector.start(Map.of());
    collector.end();

    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    final Element queryResults =
        (Element)
            factory
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(out.toByteArray()))
                .getElementsByTagNameNS("urn:epcglobal:epcis-query:xsd:2", "QueryResults")
                .item(0);
    assertNotNull(queryResults, out.toString());
    final List<String> children = new ArrayList<>();
    for (Node child = queryResults.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element element) {
        children.add(element.getLocalName() + "=" + element.getTextContent().trim());
      }
    }
    assertEquals(
        List.of("queryName=SimpleEventQuery", "subscriptionID=sub-1", "resultsBody="),
        children,
        out.toString());
  }
}
