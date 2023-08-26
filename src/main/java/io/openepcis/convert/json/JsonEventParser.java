/*
 * Copyright 2022-2023 benelog GmbH & Co. KG
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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openepcis.constants.EPCIS;
import io.openepcis.convert.collector.EPCISEventCollector;
import io.openepcis.convert.collector.EventHandler;
import io.openepcis.convert.exception.FormatConverterException;
import io.openepcis.convert.util.IndentingXMLStreamWriter;
import io.openepcis.convert.util.NonEPCISNamespaceXMLStreamWriter;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.XmlSupportExtension;
import io.openepcis.model.epcis.util.DefaultJsonSchemaNamespaceURIResolver;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.persistence.jaxb.MarshallerProperties;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public abstract class JsonEventParser {

    private static final XMLOutputFactory XML_OUTPUT_FACTORY = XMLOutputFactory.newInstance();
    protected final DefaultJsonSchemaNamespaceURIResolver defaultJsonSchemaNamespaceURIResolver =
            DefaultJsonSchemaNamespaceURIResolver.getContext();

    protected Optional<Function<Object, Object>> epcisEventMapper = Optional.empty();

    // Variable to ensure whether provided InputStream is EPCIS document or single event
    boolean isDocument = false;

    // To read the JSON-LD events using the Jackson
    protected final ObjectMapper objectMapper =
            new ObjectMapper()
                    .registerModule(
                            new SimpleModule()
                                    .addDeserializer(JsonNode.class, new JsonNodeDupeFieldHandlingDeserializer()))
                    .registerModule(new JavaTimeModule());


    protected void validateJsonStream(InputStream jsonStream) {
        if (jsonStream == null) {
            throw new FormatConverterException(
                    "Unable to convert the events from JSON - XML as InputStream contain any values");
        }
    }

    protected EPCISEvent processSingleEvent(AtomicInteger sequenceInEventList, JsonParser jsonParser) throws IOException {
        XmlSupportExtension singleEvent =
                objectMapper.readValue(jsonParser, XmlSupportExtension.class);

        EPCISEvent event = (EPCISEvent) singleEvent.xmlSupport();
        if (epcisEventMapper.isPresent()) {
            //Change the key value to keep key as localname and value as namespaceURI
            final Map<String, String> swappedNamespace = defaultJsonSchemaNamespaceURIResolver.getAllNamespaces().entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
            event.setContextInfo(!swappedNamespace.isEmpty() ? List.of(swappedNamespace) : null);
            event.getOpenEPCISExtension().setSequenceInEPCISDoc(sequenceInEventList.incrementAndGet());
            return (EPCISEvent) epcisEventMapper.get().apply(event);
        }
        return event;
    }

    protected void collectNameSpaceAndContextValues(JsonParser jsonParser) throws IOException {
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
                            defaultJsonSchemaNamespaceURIResolver.populateDocumentNamespaces(
                                    jsonParser.getText(), jsonParser.getCurrentName());
                        }
                    }
                }
            }
            jsonParser.nextToken();
        }
    }
    protected void collectDocumentMetaData(Map<String, String> contextValues, JsonParser jsonParser, EventHandler eventHandler) throws IOException {
        while (!jsonParser.getText().equals(EPCIS.EVENT_LIST_IN_CAMEL_CASE)) {

            boolean isEPCISDocument = false;
            // If the element is type then accordingly set the value EPCISDocument/EPCISQueryDocument
            if (jsonParser.getCurrentName().equals(EPCIS.TYPE)) {
                // Set for EPCISDocument or EPCISQueryDocument for adding the header element
                isEPCISDocument = jsonParser.getText().equalsIgnoreCase(EPCIS.EPCIS_DOCUMENT);
                eventHandler.setIsEPCISDocument(isEPCISDocument);
            }

            // For EPCISQueryDocument set SubscriptionID and QueryName for XML writing
            if (!isEPCISDocument) {
                if (jsonParser.getCurrentName().equalsIgnoreCase(EPCIS.SUBSCRIPTION_ID)) {
                    eventHandler.setSubscriptionID(jsonParser.nextTextValue());
                } else if (jsonParser.getCurrentName().equalsIgnoreCase(EPCIS.QUERY_NAME)) {
                    eventHandler.setQueryName(jsonParser.nextTextValue());
                }
            }

            if ((jsonParser.getCurrentToken() == JsonToken.VALUE_STRING
                    || jsonParser.getCurrentToken() == JsonToken.VALUE_NUMBER_FLOAT)
                    && (jsonParser.getCurrentName().equalsIgnoreCase(EPCIS.SCHEMA_VERSION)
                    || jsonParser.getCurrentName().equalsIgnoreCase(EPCIS.CREATION_DATE))) {

                // Add the elements to target event header
                contextValues.put(jsonParser.getCurrentName(), jsonParser.getText());
            }
            jsonParser.nextToken();
        }
    }

    // Method which will traverse through the eventList and read event one-by-one
    protected void eventTraverser(
            JsonParser jsonParser,
            ObjectMapper objectMapper,
            Marshaller marshaller,
            EventHandler<? extends EPCISEventCollector> eventHandler, boolean isMarshallingRequired)
            throws IOException, JAXBException, XMLStreamException {

        // StringWriter to get the converted XML from marshaller
        final StringWriter xmlEvent = new StringWriter();
        final AtomicInteger sequenceInEventList = new AtomicInteger(0);

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

                    // Create the XML based on type of incoming event type and store in StringWriter

                    Object xmlSupport = event.xmlSupport();
                    if (epcisEventMapper.isPresent()
                            && EPCISEvent.class.isAssignableFrom(xmlSupport.getClass())) {
                        final Map<String, String> swappedNamespace = defaultJsonSchemaNamespaceURIResolver.getAllNamespaces().entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
                        final EPCISEvent epcisEvent = (EPCISEvent) xmlSupport;
                        epcisEvent.setContextInfo(!swappedNamespace.isEmpty() ? List.of(swappedNamespace) : null);
                        epcisEvent.getOpenEPCISExtension().setSequenceInEPCISDoc(sequenceInEventList.incrementAndGet());
                        xmlSupport = epcisEventMapper.get().apply(xmlSupport);
                    }

                    if(isMarshallingRequired) {
                        final XMLStreamWriter skipEPCISNamespaceWriter =
                                new NonEPCISNamespaceXMLStreamWriter(
                                        new IndentingXMLStreamWriter(XML_OUTPUT_FACTORY.createXMLStreamWriter(xmlEvent)));

                        // Marshaller properties: Add the custom namespaces instead of the ns1, ns2
                        marshaller.setProperty(
                                MarshallerProperties.NAMESPACE_PREFIX_MAPPER,
                                defaultJsonSchemaNamespaceURIResolver.getAllNamespaces());

                        marshaller.marshal(xmlSupport, skipEPCISNamespaceWriter);

                        // Call the method to check if the event adheres to XSD or write into the OutputStream
                        // using the EventHandler
                        eventHandler.handler(xmlEvent);

                    } else {
                        // Create the JSON using Jackson ObjectMapper based on type of incoming event type and
                        // store
                        final String eventAsJson =
                                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(xmlSupport);
                        eventHandler.handler(eventAsJson);
                    }


                    if(isMarshallingRequired){
                        // Clear the StringWriter for next event
                        xmlEvent.getBuffer().setLength(0);
                    }

                    // Reset the namespaces stored for particular event
                    defaultJsonSchemaNamespaceURIResolver.resetEventNamespaces();
                }

            } else {
                log.error(
                        "Could not find required Event information for the particular event as \"type\" attribute missing, Proceeding to next event from EventList : "
                                + jsonNode);
            }
        }
    }

}
