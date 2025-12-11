/*
 * Copyright 2022-2024 benelog GmbH & Co. KG
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
package io.openepcis.converter.xml;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openepcis.constants.EPCIS;
import io.openepcis.converter.EventsConverter;
import io.openepcis.converter.collector.EPCISEventCollector;
import io.openepcis.converter.collector.EventHandler;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.model.epcis.util.ConversionNamespaceContext;
import io.openepcis.model.epcis.util.EPCISNamespacePrefixMapper;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.persistence.jaxb.JAXBContextProperties;

/**
 * Class for handling the conversion of EPCIS 2.0 events in XML format to EPCIS 2.0 JSON format. It
 * implements the "converter" method from interface "EventsConverter". This is Non-thread safe XML
 * to JSON converter for EPCIS events. Do not share an instance across threads. EventsConverter:
 * Public method that will be called by client during the conversions.
 */
@Slf4j
public class XmlToJsonConverter extends XMLEventParser implements EventsConverter {

  // Jackson instance to convert the unmarshalled event to JSON
  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
          .setSerializationInclusion(JsonInclude.Include.NON_NULL)
          .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

  public XmlToJsonConverter(final JAXBContext jaxbContext, final ConversionNamespaceContext nsContext) {
    super(jaxbContext, nsContext);
  }

  public XmlToJsonConverter(final JAXBContext jaxbContext) {
    this(jaxbContext, new ConversionNamespaceContext());
  }

  private XmlToJsonConverter(
      final XmlToJsonConverter parent, BiFunction<Object, List<Object>, Object> epcisEventMapper) {
    this(parent.jaxbContext, parent.nsContext);
    this.epcisEventMapper = Optional.ofNullable(epcisEventMapper);
  }

  public XmlToJsonConverter() throws JAXBException {
    this(
        JAXBContext.newInstance(
            "io.openepcis.model.epcis",
            Thread.currentThread().getContextClassLoader(),
            new HashMap<>() {
              {
                put(
                    JAXBContextProperties.NAMESPACE_PREFIX_MAPPER,
                    new EPCISNamespacePrefixMapper());
              }
            }));
  }

  /**
   * API method to convert the list of EPCIS events from XML to JSON-LD format
   *
   * @param xmlStream Stream of XML EPCIS events
   * @param eventHandler Handler to indicate what needs to be performed after conversion of each
   *     event from XML to JSON? EventValidator for validating each event, EventListCreator for
   *     creating list of events after converting along with all header information.
   *     EventValidatorAndListCreator for validating each event and creating JSON-LD with header
   *     info.
   * @throws IOException Method throws IOException when error occurred during the conversion.
   */
  @Override
  public void convert(
      InputStream xmlStream, EventHandler<? extends EPCISEventCollector> eventHandler)
      throws IOException, XMLStreamException, JAXBException {
    convert(xmlStream, eventHandler, this.jaxbContext);
  }

  /**
   * API method to convert the list of EPCIS events from XML to JSON-LD format
   *
   * @param xmlStream Stream of XML EPCIS events
   * @param eventHandler Handler to indicate what needs to be performed after conversion of each
   *     event from XML to JSON? EventValidator for validating each event, EventListCreator for
   *     creating list of events after converting along with all header information.
   *     EventValidatorAndListCreator for validating each event and creating JSON-LD with header
   *     info.
   * @param jaxbContext package/path to jaxb annotated classes.
   * @throws IOException Method throws IOException when error occurred during the conversion.
   */
  @Override
  public void convert(
      InputStream xmlStream,
      EventHandler<? extends EPCISEventCollector> eventHandler,
      JAXBContext jaxbContext)
      throws IOException, XMLStreamException, JAXBException {

    try {
      // Check if InputStream has some content if not then throw appropriate Exception
      validateXmlStrem(xmlStream);

      // Variable to ensure whether provided InputStream is EPCIS document or single event
      boolean isDocument = false;

      // Clear the namespaces before reading the document
      nsContext.resetAllNamespaces();

      // Map to store the attributes from the XML Header so can be added to final JSON
      final Map<String, String> contextAttributes = new HashMap<>();

      // Track event sequence
      final AtomicInteger sequenceInEventList = new AtomicInteger(0);

      // Create an instance of XMLStreamReader to read the events one-by-one
      final XMLStreamReader xmlStreamReader = createXmlStreamReader(xmlStream);

      // Create an instance of JAXBContext and Unmarshaller for unmarshalling the classes to
      // respective event
      final Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

      // Throw exception if invalid values are found during unmarshalling the XML
      validateXmlEvent(unmarshaller);

      // To format the JSON event after conversion
      objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

      // Navigate to next and start of the XML Elements
      xmlStreamReader.next();

      // Read Until the end of the file and unmarshall event-by-event
      boolean ended = false;
      while (xmlStreamReader.hasNext()) {

        if(xmlStreamReader.isStartElement()){
          final String name = xmlStreamReader.getLocalName();

          // Check if the initial element is one of the elements from "EVENT_TYPES" (one of EPCIS event)
          if (EPCIS.EPCIS_EVENT_TYPES.contains(name)) {

            // Get the event type
            Object event = getEvent(xmlStreamReader, unmarshaller);

            // Check if Object has some value
            if (event != null) {
              // map event
              event = applyEventMapper(sequenceInEventList, event);

              // Create the JSON using Jackson ObjectMapper based on type of incoming event type and store
              final String eventAsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(event);

              // If the provided XML is EPCIS document then add the converted event to Collectors List and proceed to next event
              if (isDocument) {
                // Call the method to check if the event adheres to JSON-Schema or write into the OutputStream using the EventHandler
                eventHandler.handler(eventAsJson);
              } else {
                // If the provided XML is Single EPCIS event then convert it and add to collector and End the execution.
                eventHandler.startSingleEvent(contextAttributes);
                eventHandler.collectSingleEvent(eventAsJson);
                eventHandler.endSingleEvent();
                return;
              }
            }

            // Directly proceed without going to xmlStreamReader.next to avoid skipping the next event.
            continue;
          } else {

            // For EPCISQueryDocument set SubscriptionID and QueryName for XML writing
            if (!eventHandler.isEPCISDocument()) {
              if (xmlStreamReader.getLocalName().equalsIgnoreCase(EPCIS.SUBSCRIPTION_ID)) {
                eventHandler.setSubscriptionID(xmlStreamReader.getElementText());
              } else if (xmlStreamReader.getLocalName().equalsIgnoreCase(EPCIS.QUERY_NAME)) {
                eventHandler.setQueryName(xmlStreamReader.getElementText());
              } else if (xmlStreamReader.getLocalName().equalsIgnoreCase(EPCIS.RESULTS_BODY_IN_CAMEL_CASE)) {
                // For QueryDocument invoke EventHandle Start to create the header information at resultsBody
                eventHandler.start(contextAttributes);
              }
            }

            if (name.toLowerCase().contains(EPCIS.DOCUMENT.toLowerCase())) {
              // Get the information related to the XML header elements till "EventList", If the element is EPCISDocument get all namespaces

              // Set the variable to true if the provided XML is EPCIS document else set to false for single EPCIS event
              isDocument = true;
              final boolean doc = name.equalsIgnoreCase(EPCIS.EPCIS_DOCUMENT);

              // Set for EPCISDocument or EPCISQueryDocument for adding the header elements
              eventHandler.setIsEPCISDocument(doc);

              // Get all Namespaces from the XML header and store it within the xmlNamespaces MAP
              prepareNameSpaces(xmlStreamReader);

              // Get all the Attributes from XML header and store it within attributes MAP for creation of final JSON
              prepareContextAttributes(contextAttributes, xmlStreamReader);

              // For EPCISDocument invoke EventHandle Start to create the header information at EPCISDocument
              if (doc) {
                eventHandler.start(contextAttributes);
              }
            }
          }
        } else if(xmlStreamReader.isEndElement()){
          // Call the EventHandle End method to end all the header objects created in the Start method.
          final String name = xmlStreamReader.getLocalName();

          if(name.equalsIgnoreCase(EPCIS.EPCIS_DOCUMENT)){
            eventHandler.end();
            ended = true;
            break;
          }
        }

        // always advance to next
        xmlStreamReader.next();
      }

      // Call the EventHandle End method in case </EPCISDocument> never explicitly fired above
      if (!ended) {
        eventHandler.end();
      }

    } catch (Exception e) {
      eventHandler.fail(e);
      throw new FormatConverterException("XML to JSON/JSON-LD conversion failed, " + e.getMessage(), e);
    }
  }

  public final XmlToJsonConverter mapWith(final BiFunction<Object, List<Object>, Object> mapper) {
    return new XmlToJsonConverter(this, mapper);
  }
}
