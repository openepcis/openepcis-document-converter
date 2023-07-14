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

import io.openepcis.constants.EPCIS;
import io.openepcis.convert.exception.FormatConverterException;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.Map;
import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.util.EventReaderDelegate;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * Class that implements the interface EPCISEventsCollector to create the final XML file with all
 * converted events from JSON - XML format.
 *
 * <p>start : Method to create the header information for the XML header. collect : Method to store
 * the converted events based on user provided OutputStream type. end : To close all the XML header
 * tags that were created in the start method
 */
public class XmlEPCISEventCollector implements EPCISEventCollector<OutputStream> {

  private final OutputStream stream;
  private final XMLEventWriter xmlEventWriter;
  private final XMLEventFactory events;

  private boolean isEPCISDocument;

  private String subscriptionID;

  private String queryName;

  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

  private static final XMLOutputFactory XML_OUTPUT_FACTORY = XMLOutputFactory.newInstance();

  static {
    XML_INPUT_FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
  }

  public XmlEPCISEventCollector(OutputStream stream) {
    this.stream = stream;
    try {
      // To write the final xml with all event and header information create XMLEventWriter
      xmlEventWriter = XML_OUTPUT_FACTORY.createXMLEventWriter(stream);
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
      XMLEventReader xer =
          new EventReaderDelegate(
              XML_INPUT_FACTORY.createXMLEventReader(new StringReader(event.toString()))) {
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
        xmlEventWriter.flush();
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
      xmlEventWriter.add(
          events.createStartElement(
              new QName(
                  isEPCISDocument
                      ? EPCIS.EPCIS_DOCUMENT_WITH_NAMESPACE
                      : EPCIS.EPCIS_QUERY_DOCUMENT_WITH_NAMESPACE),
              null,
              null));
      xmlEventWriter.add(
          isEPCISDocument
              ? events.createNamespace(EPCIS.EPCIS, EPCIS.EPCIS_2_0_XMLNS)
              : events.createNamespace(EPCIS.EPCIS_QUERY, EPCIS.EPCIS_QUERY_2_0_XMLNS));
      xmlEventWriter.add(events.createNamespace(EPCIS.XSI, EPCIS.XML_SCHEMA_INSTANCE));
      xmlEventWriter.add(events.createNamespace(EPCIS.CBV_MDA, EPCIS.CBV_MDA_URN));
      xmlEventWriter.add(
          events.createNamespace(
              EPCIS.STANDARD_BUSINESS_DOCUMENT_HEADER_PREFIX,
              EPCIS.STANDARD_BUSINESS_DOCUMENT_HEADER));

      // Add the values from JSON Context header stored in MAP to XML header
      for (Map.Entry<String, String> stringStringEntry : context.entrySet()) {
        xmlEventWriter.add(
            events.createAttribute(stringStringEntry.getKey(), stringStringEntry.getValue()));
      }

      // Add EPCISBody and EventList tag as outer tag
      xmlEventWriter.add(events.createStartElement(new QName(EPCIS.EPCIS_BODY), null, null));

      // Add additional wrapper tags for EPCISQueryDocument
      if (!isEPCISDocument) {
        xmlEventWriter.add(events.createStartElement(new QName(EPCIS.QUERY_RESULTS), null, null));

        // For EPCISQueryDocument add subscriptionID if present
        if (!StringUtils.isBlank(subscriptionID)) {
          xmlEventWriter.add(
              events.createStartElement(new QName(EPCIS.SUBSCRIPTION_ID), null, null));
          xmlEventWriter.add(events.createSpace(subscriptionID));
          xmlEventWriter.add(events.createEndElement(new QName(EPCIS.SUBSCRIPTION_ID), null));
        }

        // For EPCISQueryDocument add QueryName if present
        if (!StringUtils.isBlank(queryName)) {
          xmlEventWriter.add(events.createStartElement(new QName(EPCIS.QUERY_NAME), null, null));
          xmlEventWriter.add(events.createSpace(queryName));
          xmlEventWriter.add(events.createEndElement(new QName(EPCIS.QUERY_NAME), null));
        }

        xmlEventWriter.add(
            events.createStartElement(new QName(EPCIS.RESULTS_BODY_IN_CAMEL_CASE), null, null));
      }

      xmlEventWriter.add(events.createStartElement(new QName(EPCIS.EVENT_LIST), null, null));
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
      xmlEventWriter.add(events.createEndElement(new QName(EPCIS.EVENT_LIST), null));

      // Close additional wrapper tags for EPCISQueryDocument
      if (!isEPCISDocument) {
        xmlEventWriter.add(
            events.createEndElement(
                new QName(EPCIS.RESULTS_BODY_IN_CAMEL_CASE), null)); // end resultsBody
        xmlEventWriter.add(
            events.createEndElement(new QName(EPCIS.QUERY_RESULTS), null)); // end QueryResults
      }

      xmlEventWriter.add(events.createEndElement(new QName(EPCIS.EPCIS_BODY), null));
      xmlEventWriter.add(
          events.createEndElement(
              new QName(
                  isEPCISDocument
                      ? EPCIS.EPCIS_DOCUMENT_WITH_NAMESPACE
                      : EPCIS.EPCIS_QUERY_DOCUMENT_WITH_NAMESPACE),
              null));
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
      XMLEventReader xer =
          XML_INPUT_FACTORY.createXMLEventReader(new StringReader(event.toString()));
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
  public void setIsEPCISDocument(boolean isEPCISDocument) {
    this.isEPCISDocument = isEPCISDocument;
  }

  @Override
  public void setSubscriptionID(String subscriptionID) {
    this.subscriptionID = subscriptionID;
  }

  @Override
  public void setQueryName(String queryName) {
    this.queryName = queryName;
  }

  @Override
  public boolean isEPCISDocument() {
    return this.isEPCISDocument;
  }
}
