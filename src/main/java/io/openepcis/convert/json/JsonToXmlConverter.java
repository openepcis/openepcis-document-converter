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
package io.openepcis.convert.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openepcis.constants.EPCIS;
import io.openepcis.convert.EventsConverter;
import io.openepcis.convert.collector.EventHandler;
import io.openepcis.convert.exception.FormatConverterException;
import io.openepcis.convert.util.IndentingXMLStreamWriter;
import io.openepcis.convert.util.NonEPCISNamespaceXMLStreamWriter;
import io.openepcis.model.epcis.XmlSupportExtension;
import io.openepcis.model.epcis.util.DefaultJsonSchemaNamespaceURIResolver;
import io.openepcis.model.epcis.util.EPCISNamespacePrefixMapper;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import javax.enterprise.context.RequestScoped;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.persistence.jaxb.JAXBContextProperties;
import org.eclipse.persistence.jaxb.MarshallerProperties;

/**
 * Class for handling the conversion of EPCIS 2.0 events in JSON-LD format to EPCIS 2.0 XML format.
 * It implements the "converter" method from interface "EventsConverter". This is Non-thread safe
 * JSON to XML converter for EPCIS events. Do not share an instance across threads. EventsConverter:
 * Public method that will be called by client during the conversions.
 */
@Slf4j
@RequestScoped
public class JsonToXmlConverter implements EventsConverter {

  private final JAXBContext jaxbContext;

  private final DefaultJsonSchemaNamespaceURIResolver defaultJsonSchemaNamespaceURIResolver =
      DefaultJsonSchemaNamespaceURIResolver.getInstance();

  // To read the JSON-LD events using the Jackson
  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(
              new SimpleModule()
                  .addDeserializer(JsonNode.class, new JsonNodeDupeFieldHandlingDeserializer()))
          .registerModule(new JavaTimeModule());

  private static final XMLOutputFactory XML_OUTPUT_FACTORY = XMLOutputFactory.newInstance();

  public JsonToXmlConverter(final JAXBContext jaxbContext) {
    this.jaxbContext = jaxbContext;
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
      EventHandler<? extends io.openepcis.convert.collector.EPCISEventCollector> eventHandler)
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
      EventHandler<? extends io.openepcis.convert.collector.EPCISEventCollector> eventHandler,
      JAXBContext jaxbContext)
      throws IOException, JAXBException {

    // Check if InputStream has some content if not then throw appropriate Exception
    if (jsonStream == null) {
      throw new FormatConverterException(
          "Unable to convert the events from JSON - XML as InputStream contain any values");
    }

    // Clear the namespaces before reading the document
    defaultJsonSchemaNamespaceURIResolver.getInstance().resetAllNamespaces();

    // Create a Marshaller instance to convert to XML
    final Marshaller marshaller = jaxbContext.createMarshaller();
    // Set Marshalling properties: print formatted XML, exclude <xml> version tag for every event,
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
    marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);

    // Store the information from JSON header for creation of final XML
    final Map<String, String> contextValues = new HashMap<>();

    // Get the JSON Factory and parser Object
    try (JsonParser jsonParser = new JsonFactory().createParser(jsonStream)) {

      // To read the duplicate keys for User Extensions, ILMD and other elements in JSON-LD
      jsonParser.setCodec(objectMapper);

      // Check the first element is Object if not then invalid JSON throw error
      if (jsonParser.nextToken() != JsonToken.START_OBJECT) {
        throw new FormatConverterException(
            "Invalid JSON-LD file has been provided. JSON-LD file should start with the Object");
      }

      // Loop until type element to read the Context values and namespaces present in it
      while (!(jsonParser.getText().equals(EPCIS.TYPE)
          || jsonParser.getText().equals(EPCIS.EVENT_ID))) {
        if (jsonParser.getCurrentName() != null
            && jsonParser.getCurrentName().equalsIgnoreCase(EPCIS.CONTEXT)) {

          // Read the context value only if the value is of type array else skip to add only string
          if (jsonParser.nextToken() == JsonToken.START_ARRAY) {
            // Loop until end of the Array to obtain Context elements
            while (jsonParser.nextToken() != JsonToken.END_ARRAY) {

              // If element has name then store name and text in Map
              if (jsonParser.getCurrentName() != null
                  && jsonParser.currentToken() == JsonToken.VALUE_STRING) {
                // Add the namespaces from JSONSchema to the MAP in SchemaURIResolver based on
                // corresponding XSD
                defaultJsonSchemaNamespaceURIResolver
                    .getInstance()
                    .populateDocumentNamespaces(jsonParser.getText(), jsonParser.getCurrentName());
              }
            }
          }
        }
        jsonParser.nextToken();
      }

      try {
        XmlSupportExtension singleEvent =
            objectMapper.readValue(jsonParser, XmlSupportExtension.class);

        // Modify the Namespaces so trailing / or : is added and default values are removed
        defaultJsonSchemaNamespaceURIResolver.getInstance().modifyDocumentNamespaces();
        defaultJsonSchemaNamespaceURIResolver.getInstance().modifyEventNamespaces();

        // Set the namespaces for the marshaller
        marshaller.setProperty(
            MarshallerProperties.NAMESPACE_PREFIX_MAPPER,
            defaultJsonSchemaNamespaceURIResolver.getInstance().getAllNamespaces());

        // StringWriter to get the converted XML from marshaller
        final StringWriter singleXmlEvent = new StringWriter();

        // Marshaller properties: Add the custom namespaces instead of the ns1, ns2
        marshaller.marshal((singleEvent).xmlSupport(), singleXmlEvent);

        // Call the method to check if the event adheres to XSD or write into the OutputStream using
        // the EventHandler
        eventHandler.collectSingleEvent(singleXmlEvent);
      } catch (Exception e) {
        // Loop until the start of the EPCIS EventList array and prepare the XML header elements
        while (!jsonParser.getText().equals(EPCIS.EVENT_LIST_IN_CAMEL_CASE)) {
          if ((jsonParser.getCurrentToken() == JsonToken.VALUE_STRING
                  || jsonParser.getCurrentToken() == JsonToken.VALUE_NUMBER_FLOAT)
              && (jsonParser.getCurrentName().equalsIgnoreCase(EPCIS.SCHEMA_VERSION)
                  || jsonParser.getCurrentName().equalsIgnoreCase(EPCIS.CREATION_DATE))) {

            // Add the elements from JSON Header to XML header
            contextValues.put(jsonParser.getCurrentName(), jsonParser.getText());
          }
          jsonParser.nextToken();
        }

        // Goto the next token
        jsonParser.nextToken();

        // this will prepare document header, epcisBody, eventList elements
        eventHandler.start(contextValues);

        // Call the method to loop until the end of the events file
        eventTraverser(jsonParser, objectMapper, marshaller, eventHandler);

        // Call the End method to close all the header and other tags for the XML file
        eventHandler.end();

        // Close the JSON Parser after completing the reading of all contents
        jsonParser.close();
      }

    } catch (Exception e) {
      throw new FormatConverterException("Exception during the reading of JSON-LD file : " + e);
    }
    // Close JSONParser after reading all events
  }

  // Method which will traverse through the eventList and read event one-by-one
  private void eventTraverser(
      JsonParser jsonParser,
      ObjectMapper objectMapper,
      Marshaller marshaller,
      EventHandler<? extends io.openepcis.convert.collector.EPCISEventCollector> eventHandler)
      throws IOException, JAXBException, XMLStreamException {

    // StringWriter to get the converted XML from marshaller
    final StringWriter xmlEvent = new StringWriter();

    final XMLStreamWriter skipEPCISNamespaceWriter =
        new NonEPCISNamespaceXMLStreamWriter(
            new IndentingXMLStreamWriter(XML_OUTPUT_FACTORY.createXMLStreamWriter(xmlEvent)));

    // Loop until the end of the EPCIS events file
    while (jsonParser.nextToken() != JsonToken.END_ARRAY) {

      // Get the node
      final JsonNode jsonNode = jsonParser.readValueAsTree();

      // Check if the JsonNode is valid and contains the values if not throw error for particular
      // event
      if (!(jsonNode == null || jsonNode.get(EPCIS.TYPE) == null)) {

        // Based on eventType call different type of class
        XmlSupportExtension event = objectMapper.treeToValue(jsonNode, XmlSupportExtension.class);

        // If event has some value then perform the Marshalling and call handling methods based on
        // User input
        if (event != null) {
          // Modify the Namespaces so trailing / or : is added and default values are removed
          defaultJsonSchemaNamespaceURIResolver.getInstance().modifyDocumentNamespaces();
          defaultJsonSchemaNamespaceURIResolver.getInstance().modifyEventNamespaces();

          // Marshaller properties: Add the custom namespaces instead of the ns1, ns2
          marshaller.setProperty(
              MarshallerProperties.NAMESPACE_PREFIX_MAPPER,
              defaultJsonSchemaNamespaceURIResolver.getInstance().getAllNamespaces());

          // Create the XML based on type of incoming event type and store in StringWriter
          marshaller.marshal(event.xmlSupport(), skipEPCISNamespaceWriter);

          // Call the method to check if the event adheres to XSD or write into the OutputStream
          // using the EventHandler
          eventHandler.handler(xmlEvent);

          // Clear the StringWriter for next event
          xmlEvent.getBuffer().setLength(0);

          // Reset the namespaces stored for particular event
          defaultJsonSchemaNamespaceURIResolver.getInstance().resetEventNamespaces();
        }

      } else {
        log.error(
            "Could not find required Event information for the particular event as \"type\" attribute missing, Proceeding to next event from EventList : "
                + jsonNode);
      }
    }
  }
}
