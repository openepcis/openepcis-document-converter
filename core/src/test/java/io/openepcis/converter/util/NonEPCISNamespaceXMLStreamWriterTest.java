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
package io.openepcis.converter.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class NonEPCISNamespaceXMLStreamWriterTest {

  private static final String GS1 = "https://ref.gs1.org/voc/";

  @Test
  void skippedWellKnownNamespaceIsDeclaredWhereTheFragmentUsesIt() throws Exception {
    final StringWriter out = new StringWriter();
    final XMLStreamWriter writer =
        new NonEPCISNamespaceXMLStreamWriter(XMLOutputFactory.newInstance().createXMLStreamWriter(out));
    writer.writeStartElement("", "ObjectEvent", "");
    writer.writeNamespace("gs1", GS1); // skipped: gs1 is a well-known namespace
    writer.writeStartElement("gs1", "masterDataAvailableFor", GS1);
    writer.writeNamespace("gs1", GS1); // must not be declared twice
    writer.writeStartElement("gs1", "id", GS1);
    writer.writeCharacters("urn:epc:id:sgtin:9521234.000001.BAT2024-001");
    writer.writeEndElement();
    writer.writeEmptyElement("gs1", "flag", GS1);
    writer.writeEndElement();
    writer.writeEndElement();
    writer.flush();

    final String xml = out.toString();
    final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    final var doc =
        assertDoesNotThrow(
            () -> dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))),
            xml);
    assertEquals(1, doc.getElementsByTagNameNS(GS1, "masterDataAvailableFor").getLength(), xml);
    assertEquals(1, doc.getElementsByTagNameNS(GS1, "id").getLength(), xml);
    assertEquals(1, doc.getElementsByTagNameNS(GS1, "flag").getLength(), xml);
    // declared once, on the outermost element using it, not on ObjectEvent
    assertEquals(xml.indexOf("xmlns:gs1"), xml.lastIndexOf("xmlns:gs1"), xml);
    assertFalse(xml.startsWith("<ObjectEvent xmlns:gs1"), xml);
  }

  // the JDK's non-repairing writer, which writes no declaration on its own
  private static XMLStreamWriter nonRepairing(final StringWriter out) throws Exception {
    final XMLOutputFactory factory = XMLOutputFactory.newInstance();
    factory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.FALSE);
    return new NonEPCISNamespaceXMLStreamWriter(factory.createXMLStreamWriter(out));
  }

  private static Document parse(final String xml) {
    final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    return assertDoesNotThrow(
        () -> dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))),
        xml);
  }

  @Test
  void attributeByNamespaceUriBindsTheSkippedPrefix() throws Exception {
    final StringWriter out = new StringWriter();
    final XMLStreamWriter writer = nonRepairing(out);
    writer.writeStartElement("", "ObjectEvent", "");
    writer.writeNamespace("gs1", GS1); // skipped: gs1 is a well-known namespace
    writer.writeStartElement("", "masterData", "");
    writer.writeAttribute(GS1, "locationRole", "BACKROOM");
    writer.writeEndElement();
    writer.writeEndElement();
    writer.flush();

    final String xml = out.toString();
    final Element masterData = (Element) parse(xml).getElementsByTagName("masterData").item(0);
    assertEquals("BACKROOM", masterData.getAttributeNS(GS1, "locationRole"), xml);
    assertEquals("gs1", masterData.getAttributeNodeNS(GS1, "locationRole").getPrefix(), xml);
  }

  @Test
  void attributeByNamespaceUriOfANamespaceNeverDeclaredTakesTheWellKnownPrefix() throws Exception {
    final StringWriter out = new StringWriter();
    final XMLStreamWriter writer = nonRepairing(out);
    writer.writeStartElement("", "ObjectEvent", "");
    writer.writeEmptyElement("", "masterData", "");
    writer.writeAttribute(GS1, "locationRole", "BACKROOM");
    writer.writeEndElement();
    writer.flush();

    final String xml = out.toString();
    final Element masterData = (Element) parse(xml).getElementsByTagName("masterData").item(0);
    assertEquals("BACKROOM", masterData.getAttributeNS(GS1, "locationRole"), xml);
    assertEquals("gs1", masterData.getAttributeNodeNS(GS1, "locationRole").getPrefix(), xml);
  }

  @Test
  void defaultNamespaceElementIsNotWrittenWithoutItsNamespace() throws Exception {
    final StringWriter out = new StringWriter();
    final XMLStreamWriter writer = nonRepairing(out);
    writer.writeStartElement("", "ObjectEvent", "");
    // three ways a caller declares the default namespace, all of a skipped URI
    writer.writeStartElement("", "masterDataAvailableFor", GS1);
    writer.writeNamespace("", GS1);
    writer.writeStartElement("", "id", GS1);
    writer.writeCharacters("urn:epc:id:sgtin:9521234.000001.BAT2024-001");
    writer.writeEndElement();
    writer.writeEndElement();
    writer.writeStartElement("", "masterDataAvailableFor", GS1);
    writer.writeDefaultNamespace(GS1);
    writer.writeEndElement();
    writer.writeEmptyElement("", "masterDataAvailableFor", GS1);
    writer.writeEndElement();
    writer.flush();

    final String xml = out.toString();
    final Document doc = parse(xml);
    assertEquals(3, doc.getElementsByTagNameNS(GS1, "masterDataAvailableFor").getLength(), xml);
    assertEquals(1, doc.getElementsByTagNameNS(GS1, "id").getLength(), xml);
    assertEquals(0, doc.getElementsByTagNameNS(null, "masterDataAvailableFor").getLength(), xml);
    // ObjectEvent stays in no namespace, and no element declares the default namespace twice
    assertEquals(null, doc.getDocumentElement().getNamespaceURI(), xml);
    assertFalse(xml.matches("(?s).*xmlns=\"[^\"]*\"[^>]*xmlns=.*"), xml);
  }
}
