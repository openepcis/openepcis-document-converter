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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

/** Assertions on the master data fixture masterDataAvailableForExtension (XML and JSON). */
public final class MasterDataAssertions {

  private static final String GS1 = "https://ref.gs1.org/voc/";

  /**
   * Well-formed, one ObjectEvent, and inside it one gs1:masterDataAvailableFor with each element of
   * the fixture at its place: id, type, countryOfOrigin/countryCode, regulatoryInformation with
   * type, regulationType/id and regulatoryAct.
   */
  public static void assertMasterDataKept(String xml, String productId) {
    javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    org.w3c.dom.Document doc = assertDoesNotThrow(
        () -> dbf.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))),
        () -> "output must be well-formed XML:\n" + xml);
    assertEquals(1, doc.getElementsByTagName("ObjectEvent").getLength(), xml);
    org.w3c.dom.NodeList masterData = doc.getElementsByTagNameNS(GS1, "masterDataAvailableFor");
    assertEquals(1, masterData.getLength(), "master data must be kept:\n" + xml);
    org.w3c.dom.Element md = (org.w3c.dom.Element) masterData.item(0);
    assertTrue(hasAncestor(md, "ObjectEvent"), xml);

    assertEquals(productId, text(child(md, "id", xml)), xml);
    assertEquals("Product", text(child(md, "type", xml)), xml);
    assertEquals("DE", text(child(child(md, "countryOfOrigin", xml), "countryCode", xml)), xml);
    org.w3c.dom.Element regulatory = child(md, "regulatoryInformation", xml);
    assertEquals("RegulatoryInformation", text(child(regulatory, "type", xml)), xml);
    assertEquals(
        "gs1:RegulationTypeCode-BATTERY_DIRECTIVE",
        text(child(child(regulatory, "regulationType", xml), "id", xml)),
        xml);
    assertEquals("EU 2023/1542", text(child(regulatory, "regulatoryAct", xml)), xml);
  }

  // The one direct child element of that local name. In the fixture the children are unqualified;
  // openepcis-models before its extension reader fix (openepcis-models#77) writes them in the
  // parent's gs1 namespace, which this PR does not depend on, so both are accepted.
  private static org.w3c.dom.Element child(org.w3c.dom.Element parent, String localName, String xml) {
    org.w3c.dom.Element found = null;
    int count = 0;
    for (org.w3c.dom.Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof org.w3c.dom.Element e
          && localName.equals(e.getLocalName())
          && (e.getNamespaceURI() == null || GS1.equals(e.getNamespaceURI()))) {
        found = e;
        count++;
      }
    }
    assertEquals(1, count, parent.getLocalName() + " must have one " + localName + ":\n" + xml);
    return found;
  }

  private static String text(org.w3c.dom.Element element) {
    return element.getTextContent().trim();
  }

  private static boolean hasAncestor(org.w3c.dom.Node node, String localName) {
    for (org.w3c.dom.Node n = node.getParentNode(); n != null; n = n.getParentNode()) {
      if (localName.equals(n.getLocalName()) || localName.equals(n.getNodeName())) {
        return true;
      }
    }
    return false;
  }

  private MasterDataAssertions() {}
}
