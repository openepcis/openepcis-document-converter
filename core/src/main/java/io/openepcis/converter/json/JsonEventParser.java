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
package io.openepcis.converter.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openepcis.constants.EPCIS;
import io.openepcis.converter.collector.EPCISEventCollector;
import io.openepcis.converter.collector.EventHandler;
import io.openepcis.converter.collector.context.ContextProcessor;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.converter.util.IndentingXMLStreamWriter;
import io.openepcis.converter.util.NamespaceUsageScanner;
import io.openepcis.converter.util.NonEPCISNamespaceXMLStreamWriter;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.XmlSupportExtension;
import io.openepcis.model.epcis.modifier.CustomExtensionAdapter;
import io.openepcis.model.epcis.util.ConversionNamespaceContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.persistence.jaxb.MarshallerProperties;

public abstract class JsonEventParser {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JsonEventParser.class);
    private static final XMLOutputFactory XML_OUTPUT_FACTORY = XMLOutputFactory.newInstance();
    protected final ConversionNamespaceContext nsContext;
    protected final ContextProcessor contextProcessor = ContextProcessor.getInstance();
    protected Optional<BiFunction<Object, List<Object>, Object>> epcisEventMapper = Optional.empty();
    // Variable to ensure whether provided InputStream is EPCIS document or single event
    boolean isDocument = false;
    // To read the JSON-LD events using the Jackson
    protected final ObjectMapper objectMapper = new ObjectMapper().registerModule(new SimpleModule().addDeserializer(JsonNode.class, new JsonNodeDupeFieldHandlingDeserializer())).registerModule(new JavaTimeModule());

    public JsonEventParser(ConversionNamespaceContext nsContext) {
        this.nsContext = nsContext;
    }

    protected void validateJsonStream(InputStream jsonStream) {
        if (jsonStream == null) {
            throw new FormatConverterException("Unable to convert the events from JSON - XML as InputStream contain any values");
        }
    }

    protected EPCISEvent processSingleEvent(AtomicInteger sequenceInEventList, JsonParser jsonParser) throws IOException {
        // Read the @context information from bare event no matter where it appears not just when it appears before type
        final ObjectReader singleEventReader =
                nsContext != null
                        ? objectMapper.readerFor(XmlSupportExtension.class).withAttribute(ConversionNamespaceContext.ATTR_KEY, nsContext)
                        : objectMapper.readerFor(XmlSupportExtension.class);
        final XmlSupportExtension singleEvent = singleEventReader.readValue(jsonParser);
        final EPCISEvent event = (EPCISEvent) singleEvent.xmlSupport();

        if (epcisEventMapper.isPresent()) {
            // Change the key value to keep key as local name and value as namespaceURI
            final Map<String, String> swappedNamespace =
                    nsContext != null
                            ? nsContext.getAllNamespaces().entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey))
                            : Map.of();
            event.getOpenEPCISExtension().setSequenceInEPCISDoc(sequenceInEventList.incrementAndGet());
            return (EPCISEvent) epcisEventMapper.get().apply(event, List.of(swappedNamespace));
        }
        return event;
    }

    // Parse the @context in JSON document and read the values and store the elements for document
    // level
    protected void collectNameSpaceAndContextValues(JsonParser jsonParser, final Map<String, String> contextValues) throws IOException {
        // Loop over the jsonParser until reaching the type : EPCISDocument or eventId field at document
        // level
        while (jsonParser.currentToken() != null &&
                !(EPCIS.TYPE.equals(jsonParser.getText()) || EPCIS.EVENT_ID.equals(jsonParser.getText()))) {
            // If values are present before context, store as metadata in contextValues and add as
            // attributes to epcis:EPCISDocument in XML
            if (StringUtils.isNotBlank(jsonParser.currentName()) && StringUtils.isNotBlank(jsonParser.getText()) && !EPCIS.CONTEXT.equalsIgnoreCase(jsonParser.currentName())) {
                contextValues.put(jsonParser.currentName(), jsonParser.getText());
            }
            // Read the context value only if the value is of type array else skip to add only string
            if (jsonParser.currentName() != null && EPCIS.CONTEXT.equalsIgnoreCase(jsonParser.currentName()) && jsonParser.nextToken() == JsonToken.START_ARRAY) {
                // Loop until end of the Array to obtain Context elements
                while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
                    // Get the default context value from the JSON @context using the ContextLogicDelegator
                    // find if its custom context or Default GS1 context
                    if (jsonParser.currentToken() == JsonToken.VALUE_STRING && nsContext != null) {
                        contextProcessor.resolveForXmlConversion(Map.of(jsonParser.getText(), jsonParser.getText()), nsContext);
                    }
                    // Store namespace/context with prefix in Map if valid (ignores @ namespaces and default
                    // context matching in EPCISNamespacePrefixMapper.EPCIS_NAMESPACE_MAP)
                    if (jsonParser.currentName() != null && jsonParser.currentToken() == JsonToken.VALUE_STRING && !jsonParser.currentName().startsWith("@") && !EPCIS.EPCIS_DEFAULT_NAMESPACES.containsValue(jsonParser.getText()) && !EPCIS.GS1_EPCIS_DOMAIN.equalsIgnoreCase(jsonParser.getText()) && nsContext != null) {
                        // Add namespaces from JSON schema to the map (key: remote URL/URN and value: prefix
                        // associated to URL)
                        nsContext.populateDocumentNamespaces(jsonParser.getText(), jsonParser.currentName());
                    }
                }
            }
            jsonParser.nextToken();
        }
    }

    // Reads an @context array at the current position and registers its prefixes.
    protected void readContextNamespaces(final JsonParser jsonParser) throws IOException {
        if (jsonParser.currentName() == null
                || !EPCIS.CONTEXT.equalsIgnoreCase(jsonParser.currentName())
                || jsonParser.nextToken() != JsonToken.START_ARRAY) {
            return;
        }
        JsonToken token;

        while ((token = jsonParser.nextToken()) != null && token != JsonToken.END_ARRAY) {
            // process @context array
            if (token == JsonToken.VALUE_STRING && nsContext != null) {
                contextProcessor.resolveForXmlConversion(Map.of(jsonParser.getText(), jsonParser.getText()), nsContext);
            }

            if (jsonParser.currentName() != null && token == JsonToken.VALUE_STRING
                    && !jsonParser.currentName().startsWith("@")
                    && !EPCIS.EPCIS_DEFAULT_NAMESPACES.containsValue(jsonParser.getText())
                    && !EPCIS.GS1_EPCIS_DOMAIN.equalsIgnoreCase(jsonParser.getText())
                    && nsContext != null) {
                nsContext.populateDocumentNamespaces(jsonParser.getText(), jsonParser.currentName());
            }
        }
    }

    protected void collectDocumentMetaData(final Map<String, String> contextValues, final JsonParser jsonParser, final EventHandler eventHandler) throws IOException {
        boolean isEPCISDocument = false;
        // Loop till the eventList and obtain the information at the document level like creationDate,
        // schemaVersion, type etc.
        while (jsonParser.currentToken() != null && !EPCIS.EVENT_LIST_IN_CAMEL_CASE.equals(jsonParser.getText())) {
            // A document level @context may sit anywhere before epcisBody, not only before type
            readContextNamespaces(jsonParser);

            // If the element is type then accordingly set the value EPCISDocument/EPCISQueryDocument
            if (EPCIS.TYPE.equals(jsonParser.currentName())) {
                // Set for EPCISDocument or EPCISQueryDocument for adding the header element
                isEPCISDocument = EPCIS.EPCIS_DOCUMENT.equalsIgnoreCase(jsonParser.getText());
                eventHandler.setIsEPCISDocument(isEPCISDocument);
            }
            // For EPCISQueryDocument set SubscriptionID and QueryName for XML writing
            if (!isEPCISDocument) {
                if (EPCIS.SUBSCRIPTION_ID.equalsIgnoreCase(jsonParser.currentName())) {
                    eventHandler.setSubscriptionID(jsonParser.nextTextValue());
                } else if (EPCIS.QUERY_NAME.equalsIgnoreCase(jsonParser.currentName())) {
                    eventHandler.setQueryName(jsonParser.nextTextValue());
                }
            }
            if ((jsonParser.getCurrentToken() == JsonToken.VALUE_STRING || jsonParser.getCurrentToken() == JsonToken.VALUE_NUMBER_FLOAT) && (EPCIS.SCHEMA_VERSION.equalsIgnoreCase(jsonParser.currentName()) || EPCIS.CREATION_DATE.equalsIgnoreCase(jsonParser.currentName()))) {
                // Add the elements to target event header
                contextValues.put(jsonParser.currentName(), jsonParser.getText());
            }
            jsonParser.nextToken();
        }
    }

    // Method which will traverse through the eventList and read event one-by-one
    protected void eventTraverser(JsonParser jsonParser, ObjectMapper objectMapper, Marshaller marshaller, EventHandler<? extends EPCISEventCollector> eventHandler, boolean isMarshallingRequired) throws IOException, JAXBException, XMLStreamException {
        // StringWriter to get the converted XML from marshaller
        final StringWriter xmlEvent = new StringWriter();
        final AtomicInteger sequenceInEventList = new AtomicInteger(0);
        // Loop until the end of the EPCIS events file
        JsonToken eventToken;
        while ((eventToken = jsonParser.nextToken()) != null && eventToken != JsonToken.END_ARRAY) {
            // Get the node
            final JsonNode jsonNode = jsonParser.readValueAsTree();
            // Check if the JsonNode is valid and contains the values if not throw error for particular
            // event
            if (!(jsonNode == null || jsonNode.get(EPCIS.TYPE) == null)) {
                // Based on eventType call different type of class
                final ObjectReader eventReader = nsContext != null
                        ? objectMapper.readerFor(XmlSupportExtension.class).withAttribute(ConversionNamespaceContext.ATTR_KEY, nsContext)
                        : objectMapper.readerFor(XmlSupportExtension.class);
                XmlSupportExtension event = eventReader.readValue(jsonNode);

                // If event has some value then perform the Marshalling and call handling methods based on
                // User input
                if (event != null) {
                    // Create the XML based on type of incoming event type and store in StringWriter
                    Object xmlSupport = event.xmlSupport();
                    if (epcisEventMapper.isPresent() && EPCISEvent.class.isAssignableFrom(xmlSupport.getClass())) {
                        final Map<String, String> swappedNamespace = nsContext != null ? nsContext.getAllNamespaces().entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (existing, replacement) -> existing)) : Map.of();
                        final EPCISEvent epcisEvent = (EPCISEvent) xmlSupport;
                        epcisEvent.getOpenEPCISExtension().setSequenceInEPCISDoc(sequenceInEventList.incrementAndGet());
                        xmlSupport = epcisEventMapper.get().apply(xmlSupport, List.of(swappedNamespace));
                    }
                    if (isMarshallingRequired) {
                        // Bind the namespaces this event uses, the same rule the reactive path applies
                        final Set<String> usedNamespaceUris = nsContext != null ? NamespaceUsageScanner.scan(jsonNode, nsContext) : Set.of();
                        final XMLStreamWriter skipEPCISNamespaceWriter = new NonEPCISNamespaceXMLStreamWriter(
                                new IndentingXMLStreamWriter(XML_OUTPUT_FACTORY.createXMLStreamWriter(xmlEvent)), Set.of(), usedNamespaceUris);

                        // Marshaller properties: Add the custom namespaces instead of the ns1, ns2
                        final Map<String, String> allNamespaces = nsContext != null ? nsContext.getAllNamespaces() : Map.of();
                        marshaller.setProperty(MarshallerProperties.NAMESPACE_PREFIX_MAPPER, allNamespaces);
                        if (nsContext != null) {
                            marshaller.setAdapter(CustomExtensionAdapter.class, new CustomExtensionAdapter(nsContext));
                        }
                        marshaller.marshal(xmlSupport, skipEPCISNamespaceWriter);
                        // Call the method to check if the event adheres to XSD or write into the OutputStream
                        // using the EventHandler
                        eventHandler.handler(xmlEvent);
                    } else {
                        // Create the JSON using Jackson ObjectMapper based on type of incoming event type and
                        // store
                        final String eventAsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(xmlSupport);
                        eventHandler.handler(eventAsJson);
                    }
                    if (isMarshallingRequired) {
                        // Clear the StringWriter for next event
                        xmlEvent.getBuffer().setLength(0);
                    }
                    // Reset the namespaces stored for particular event
                    if (nsContext != null) {
                        nsContext.resetEventNamespaces();
                    }
                }
            } else {
                log.error("Could not find required Event information for the particular event as \"type\" attribute missing, Proceeding to next event from EventList : " + jsonNode);
            }
        }
    }
}
