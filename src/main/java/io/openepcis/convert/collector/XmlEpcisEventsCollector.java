/*
 * Copyright 2022 benelog GmbH & Co. KG
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
package io.openepcis.convert.collector;

import io.openepcis.convert.exception.FormatConverterException;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.Map;
import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.util.EventReaderDelegate;

/**
 * Class that implements the interface EPCISEventsCollector to create the final XML file with all
 * converted events from JSON - XML format.
 *
 * <p>start : Method to create the header information for the XML header. collect : Method to store
 * the converted events based on user provided OutputStream type. end : To close all the XML header
 * tags that were created in the start method
 */
public class XmlEpcisEventsCollector implements EpcisEventsCollector<OutputStream> {

  private final OutputStream stream;
  private final XMLEventWriter xmlEventWriter;
  private final XMLEventFactory events;

  public XmlEpcisEventsCollector(OutputStream stream) {
    this.stream = stream;
    try {
      // To write the final xml with all event and header information create XMLEventWriter
      xmlEventWriter = XMLOutputFactory.newInstance().createXMLEventWriter(stream);

      // To create and add elements to final XML with all elements
      events = XMLEventFactory.newInstance();
    } catch (XMLStreamException e) {
      throw new FormatConverterException(
          "Exception during JSON-XML conversion, Error occurred during the creation of XMLEventWriter : "
              + e);
    }
  }

  public void collect(Object event) {
    try {
      final XMLInputFactory factory = XMLInputFactory.newInstance();
      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
      XMLEventReader xer =
          new EventReaderDelegate(
              factory.createXMLEventReader(new StringReader(event.toString()))) {
            @Override
            public boolean hasNext() {
              if (!super.hasNext()) return false;
              try {
                return !super.peek().isEndDocument();
              } catch (XMLStreamException ignored) {
                return true;
              }
            }
          };
      if (xer.peek().isStartDocument()) {
        xer.nextEvent();
        xmlEventWriter.add(xer);
      }
    } catch (XMLStreamException e) {
      throw new FormatConverterException(
          "Exception during JSON-XML conversion, Error occurred during the addition of events to XMLEventWriter: "
              + e);
    }
  }

  @Override
  public OutputStream get() {
    return stream;
  }

  @Override
  public void start(Map<String, String> context) {
    try {
      // Start the EPCIS document and add the header elements
      xmlEventWriter.add(events.createStartDocument());
      xmlEventWriter.add(events.createStartElement(new QName("epcis:EPCISDocument"), null, null));
      xmlEventWriter.add(events.createNamespace("epcis", "urn:epcglobal:epcis:xsd:2"));
      xmlEventWriter.add(
          events.createNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance"));
      xmlEventWriter.add(events.createNamespace("cbvmda", "urn:epcglobal:cbv:mda:"));

      // Add the values from JSON Context header stored in MAP to XML header
      for (Map.Entry<String, String> stringStringEntry : context.entrySet()) {
        xmlEventWriter.add(
            events.createAttribute(stringStringEntry.getKey(), stringStringEntry.getValue()));
      }

      // Add EPCISBody and EventList tag as outer tag
      xmlEventWriter.add(events.createStartElement(new QName("EPCISBody"), null, null));
      xmlEventWriter.add(events.createStartElement(new QName("EventList"), null, null));
    } catch (XMLStreamException e) {
      throw new FormatConverterException(
          "Exception during JSON-XML conversion, Error occurred during the creation of final XML file header information "
              + e);
    }
  }

  @Override
  public void end() {
    try {
      // End the EventList, EPCISBody, EPCISDocument and the while document after completing all
      // files writing
      xmlEventWriter.add(events.createEndElement(new QName("EventList"), null));
      xmlEventWriter.add(events.createEndElement(new QName("EPCISBody"), null));
      xmlEventWriter.add(events.createEndElement(new QName("epcis:Document"), null));
      xmlEventWriter.add(events.createEndDocument());
      xmlEventWriter.close();
    } catch (XMLStreamException e) {
      throw new FormatConverterException(
          "Exception during JSON-XML conversion, Error occurred during the closing of xmlEventWriter:"
              + e);
    }
  }

  @Override
  public void collectSingleEvent(Object event) {
    try {
      final XMLInputFactory factory = XMLInputFactory.newInstance();
      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
      XMLEventReader xer = factory.createXMLEventReader(new StringReader(event.toString()));
      if (xer.peek().isStartDocument()) {
        xer.nextEvent();
        xmlEventWriter.add(xer);
      }
    } catch (XMLStreamException e) {
      throw new FormatConverterException(
          "Exception during JSON-XML conversion, Error occurred during the addition of events to XMLEventWriter: "
              + e);
    }
  }
}
