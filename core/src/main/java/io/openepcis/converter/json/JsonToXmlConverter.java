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
package io.openepcis.converter.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openepcis.converter.EventsConverter;
import io.openepcis.converter.collector.EventHandler;
import io.openepcis.converter.collector.EPCISEventCollector;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.converter.util.IndentingXMLStreamWriter;
import io.openepcis.converter.util.NonEPCISNamespaceXMLStreamWriter;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.util.EPCISNamespacePrefixMapper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.persistence.jaxb.JAXBContextProperties;
import org.eclipse.persistence.jaxb.MarshallerProperties;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

/**
 * Class for handling the conversion of EPCIS 2.0 events in JSON-LD format to EPCIS 2.0 XML format.
 * It implements the "converter" method from interface "EventsConverter". This is Non-thread safe
 * JSON to XML converter for EPCIS events. Do not share an instance across threads. EventsConverter:
 * Public method that will be called by client during the conversions.
 */
@Slf4j
@RequestScoped
public class JsonToXmlConverter extends JsonEventParser implements EventsConverter {

  private final JAXBContext jaxbContext;

  private static final XMLOutputFactory XML_OUTPUT_FACTORY = XMLOutputFactory.newInstance();

  public JsonToXmlConverter(final JAXBContext jaxbContext) {
    this.jaxbContext = jaxbContext;
  }

  private JsonToXmlConverter(
      final JsonToXmlConverter parent, BiFunction<Object,List<Object>, Object> epcisEventMapper) {
    this(parent.jaxbContext);
    this.epcisEventMapper = Optional.ofNullable(epcisEventMapper);
  }

  public JsonToXmlConverter() throws JAXBException {
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
   * API method to convert the list of EPCIS events from JSON-LD to XML format
   *
   * @param jsonStream Stream of JSON EPCIS events
   * @param eventHandler Handler to indicate what needs to be performed after conversion of each
   *     event from JSON to XML? EventValidator for validating each event, EventListCreator for
   *     creating list of events after converting along with all header information.
   *     EventValidatorAndListCreator for validating each event and creating XML with header info.
   * @throws IOException Method throws IOException when error occurred during the conversion.
   */
  @Override
  public void convert(
      InputStream jsonStream,
      EventHandler<? extends EPCISEventCollector> eventHandler)
      throws IOException, JAXBException {
    convert(jsonStream, eventHandler, this.jaxbContext);
  }

  /**
   * API method to convert the list of EPCIS events from JSON-LD to XML format
   *
   * @param jsonStream Stream of JSON EPCIS events
   * @param eventHandler Handler to indicate what needs to be performed after conversion of each
   *     event from JSON to XML? EventValidator for validating each event, EventListCreator for
   *     creating list of events after converting along with all header information.
   *     EventValidatorAndListCreator for validating each event and creating XML with header info.
   * @param jaxbContext jaxbContext from required package
   * @throws IOException Method throws IOException when error occurred during the conversion.
   */
  @Override
  public void convert(
      InputStream jsonStream,
      EventHandler<? extends EPCISEventCollector> eventHandler,
      JAXBContext jaxbContext)
      throws IOException, JAXBException {

    // Check if InputStream has some content if not then throw appropriate Exception
    validateJsonStream(jsonStream);

    // Clear the namespaces before reading the document
    defaultJsonSchemaNamespaceURIResolver.resetAllNamespaces();

    // Create a Marshaller instance to convert to XML
    final Marshaller marshaller = jaxbContext.createMarshaller();
    // Set Marshalling properties: print formatted XML, exclude <xml> version tag for every event,
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
    marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);

    // Store the information from JSON header for creation of final XML
    final Map<String, String> contextValues = new HashMap<>();

    final AtomicInteger sequenceInEventList = new AtomicInteger(0);

    // Get the JSON Factory and parser Object
    try (JsonParser jsonParser = new JsonFactory().createParser(jsonStream)) {

      // To read the duplicate keys for User Extensions, ILMD and other elements in JSON-LD
      jsonParser.setCodec(objectMapper);

      // Check the first element is Object if not then invalid JSON throw error
      if (jsonParser.nextToken() != JsonToken.START_OBJECT) {
        throw new FormatConverterException("Invalid JSON-LD file has been provided. JSON-LD file should start with the Object");
      }

      // Loop until type element to read the Context values and namespaces present in it
      collectNameSpaceAndContextValues(jsonParser);

      try {

        EPCISEvent event = processSingleEvent(sequenceInEventList, jsonParser);
        // Set the namespaces for the marshaller
        marshaller.setProperty(
                MarshallerProperties.NAMESPACE_PREFIX_MAPPER,
                defaultJsonSchemaNamespaceURIResolver.getAllNamespaces());

        // StringWriter to get the converted XML from marshaller
        final StringWriter singleXmlEvent = new StringWriter();

        final XMLStreamWriter skipEPCISNamespaceWriter =
                new NonEPCISNamespaceXMLStreamWriter(
                        new IndentingXMLStreamWriter(
                                XML_OUTPUT_FACTORY.createXMLStreamWriter(singleXmlEvent)));
        // Marshaller properties: Add the custom namespaces instead of the ns1, ns2
        marshaller.marshal(event, skipEPCISNamespaceWriter);

        // Call the method to check if the event adheres to XSD or write into the OutputStream using
        // the EventHandler
        eventHandler.collectSingleEvent(singleXmlEvent);
      } catch (Exception e) {
        // Loop until the start of the EPCIS EventList array and prepare the XML header elements
        collectDocumentMetaData(contextValues, jsonParser, eventHandler);

        // Goto the next token
        jsonParser.nextToken();

        // this will prepare document header, epcisBody, eventList elements
        eventHandler.start(contextValues);

        // Call the method to loop until the end of the events file
        eventTraverser(jsonParser, objectMapper, marshaller, eventHandler, true);

        // Call the End method to close all the header and other tags for the XML file
        eventHandler.end();

        // Close the JSON Parser after completing the reading of all contents
        jsonParser.close();
      }

    } catch (Exception e) {
      throw new FormatConverterException("Exception during the reading of JSON-LD file : " + e, e);
    }
    // Close JSONParser after reading all events
  }

  public final JsonToXmlConverter mapWith(final BiFunction<Object, List<Object>,Object> mapper) {
    return new JsonToXmlConverter(this, mapper);
  }
}
