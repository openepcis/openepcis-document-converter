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
package io.openepcis.convert.xml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openepcis.constants.EPCIS;
import io.openepcis.convert.EventsConverter;
import io.openepcis.convert.collector.EPCISEventCollector;
import io.openepcis.convert.collector.EventHandler;
import io.openepcis.convert.exception.FormatConverterException;
import io.openepcis.model.epcis.*;
import io.openepcis.model.epcis.modifier.Constants;
import io.openepcis.model.epcis.util.DefaultJsonSchemaNamespaceURIResolver;
import io.openepcis.model.epcis.util.EPCISNamespacePrefixMapper;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import javax.xml.stream.XMLInputFactory;
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
public class XmlToJsonConverter implements EventsConverter {

  private final JAXBContext jaxbContext;

  private DefaultJsonSchemaNamespaceURIResolver namespaceResolver;

  public XmlToJsonConverter(final JAXBContext jaxbContext) {
    this.jaxbContext = jaxbContext;
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

    this.namespaceResolver = DefaultJsonSchemaNamespaceURIResolver.getContext();
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
      if (xmlStream == null) {
        throw new FormatConverterException(
            "Unable to convert the events from XML - JSON-LD as InputStream contains NULL values");
      }

      // Variable to ensure whether provided InputStream is EPCIS document or single event
      boolean isDocument = false;

      // Clear the namespaces before reading the document
      namespaceResolver.resetAllNamespaces();

      // Jackson instance to convert the unmarshalled event to JSON
      final ObjectMapper objectMapper =
          new ObjectMapper()
              .registerModule(new JavaTimeModule())
              .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

      // Map to store the attributes from the XML Header so can be added to final JSON
      final Map<String, String> contextAttributes = new HashMap<>();

      // Create an instance of XMLStreamReader to read the events one-by-one
      final XMLInputFactory inputFactory = XMLInputFactory.newInstance();
      inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
      inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
      final XMLStreamReader xmlStreamReader = inputFactory.createXMLStreamReader(xmlStream);

      // Create an instance of JAXBContext and Unmarshaller for unmarshalling the classes to
      // respective event
      final Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

      // Throw exception if invalid values are found during unmarshalling the XML
      unmarshaller.setEventHandler(
          validationEvent -> {
            throw new FormatConverterException(validationEvent.getMessage());
          });

      // To format the JSON event after conversion
      objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

      // Navigate to next and start of the XML Elements
      xmlStreamReader.next();

      // Read Until the end of the file and unmarshall event-by-event
      while (xmlStreamReader.hasNext()) {
        // Check if the initial element is one of the elements from "EVENT_TYPES" (one of EPCIS
        // event)
        if (xmlStreamReader.isStartElement()
            && Arrays.asList(Constants.EVENT_TYPES).contains(xmlStreamReader.getLocalName())) {

          // Get the event type
          final String epcisEvent = xmlStreamReader.getLocalName();

          Object event = null;
          // Based on eventType make unmarshaller call to respective event class
          switch (epcisEvent) {
            case EPCIS.OBJECT_EVENT ->
            // Unmarshal the ObjectEvent and Convert it to JSON-LD
            event = unmarshaller.unmarshal(xmlStreamReader, ObjectEvent.class).getValue();
            case EPCIS.AGGREGATION_EVENT ->
            // Unmarshal the AggregationEvent and Convert it to JSON-LD
            event = unmarshaller.unmarshal(xmlStreamReader, AggregationEvent.class).getValue();
            case EPCIS.TRANSACTION_EVENT ->
            // Unmarshal the TransactionEvent and Convert it to JSON-LD
            event = unmarshaller.unmarshal(xmlStreamReader, TransactionEvent.class).getValue();
            case EPCIS.TRANSFORMATION_EVENT ->
            // Unmarshal the TransformationEvent and Convert it to JSON-LD
            event = unmarshaller.unmarshal(xmlStreamReader, TransformationEvent.class).getValue();
            case EPCIS.ASSOCIATION_EVENT ->
            // Unmarshal the AssociationEvent and Convert it to JSON-LD
            event = unmarshaller.unmarshal(xmlStreamReader, AssociationEvent.class).getValue();
            default ->
            // If NONE of the EPCIS event type matches then do not convert and make a note
            log.error("JSON event does not match any of EPCIS event : {} ", epcisEvent);
          }

          // Check if Object has some value
          if (event != null) {
            // Create the JSON using Jackson ObjectMapper based on type of incoming event type and
            // store
            final String eventAsJson =
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(event);

            // If the provided XML is EPCIS document then add the converted event to Collectors List
            // and proceed to next event
            if (isDocument) {
              // Call the method to check if the event adheres to JSON-Schema or write into the
              // OutputStream using the EventHandler
              eventHandler.handler(eventAsJson);
            } else {
              // If the provided XML is Single EPCIS event then convert it and add to collector and
              // End the execution.
              eventHandler.startSingleEvent(contextAttributes);
              eventHandler.collectSingleEvent(eventAsJson);
              eventHandler.endSingleEvent();
              return;
            }
          }

        } else if (xmlStreamReader.isStartElement()
            && xmlStreamReader.getLocalName().toLowerCase().contains(EPCIS.DOCUMENT)) {
          // Get the information related to the XML header elements till "EventList", If the element
          // is EPCISDocument get all namespaces

          // Set the variable to true if the provided XML is EPCIS document else set to false for
          // single EPCIS event
          isDocument = true;

          // Get all Namespaces from the XML header and store it within the xmlNamespaces MAP
          IntStream.range(0, xmlStreamReader.getNamespaceCount())
              .forEach(
                  namespaceIndex -> {
                    // Omit the Namespace values which are already present within JSON-LD Schema by
                    // default
                    if (!Arrays.asList(Constants.PROTECTED_TERMS_OF_CONTEXT)
                        .contains(xmlStreamReader.getNamespacePrefix(namespaceIndex))) {
                      namespaceResolver.populateDocumentNamespaces(
                          xmlStreamReader.getNamespaceURI(namespaceIndex),
                          xmlStreamReader.getNamespacePrefix(namespaceIndex));
                    }
                  });

          // Get all the Attributes from XML header and store it within attributes MAP for creation
          // of final JSON
          IntStream.range(0, xmlStreamReader.getAttributeCount())
              .forEach(
                  attributeIndex -> {
                    // Omit the attribute values which are already present within JSON-LD Schema by
                    // default
                    if (Arrays.stream(Constants.PROTECTED_TERMS_OF_CONTEXT)
                        .noneMatch(
                            keyword ->
                                String.valueOf(xmlStreamReader.getAttributeName(attributeIndex))
                                    .contains(keyword))) {
                      contextAttributes.put(
                          String.valueOf(xmlStreamReader.getAttributeName(attributeIndex)),
                          xmlStreamReader.getAttributeValue(attributeIndex));
                    }
                  });

          // Call the EventHandle Start method to create the header information for the final JSON
          // file
          eventHandler.start(contextAttributes);
        }
        // Move to the next event/element in InputStream
        xmlStreamReader.next();
      }

      // Call the EventHandle End method to end all the header objects created in the Start method
      eventHandler.end();

    } catch (Exception e) {
      throw new FormatConverterException(
          "Exception occurred during the conversion of the conversion of XML to JSON-LD", e);
    }
  }
}
