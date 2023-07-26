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

import io.openepcis.constants.EPCIS;
import io.openepcis.convert.EventsConverter;
import io.openepcis.convert.collector.EPCISEventCollector;
import io.openepcis.convert.collector.EventHandler;
import io.openepcis.convert.exception.FormatConverterException;
import io.openepcis.convert.util.IndentingXMLStreamWriter;
import io.openepcis.convert.util.NonEPCISNamespaceXMLStreamWriter;
import io.openepcis.model.epcis.util.EPCISNamespacePrefixMapper;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.eclipse.persistence.jaxb.JAXBContextProperties;
import org.eclipse.persistence.jaxb.MarshallerProperties;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class XMLEventValueTransformer extends XMLEventParser implements EventsConverter {


    public XMLEventValueTransformer(final JAXBContext jaxbContext) {
        super(jaxbContext);
    }

    private XMLEventValueTransformer(
            final XMLEventValueTransformer parent, Function<Object, Object> epcisEventMapper) {
        this(parent.jaxbContext);
        this.epcisEventMapper = Optional.ofNullable(epcisEventMapper);
    }

    public XMLEventValueTransformer() throws JAXBException {
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
    public void
    convert(
            InputStream xmlStream,
            EventHandler<? extends EPCISEventCollector> eventHandler)
            throws IOException, XMLStreamException, JAXBException {
            convert(xmlStream, eventHandler, this.jaxbContext);
    }

    @Override
    public void convert(InputStream xmlStream, EventHandler<? extends EPCISEventCollector> eventHandler, JAXBContext jaxbContext) throws IOException, XMLStreamException, JAXBException {
        try {
            // Check if InputStream has some content if not then throw appropriate Exception
            validateXmlStrem(xmlStream);

            final Marshaller marshaller = createMashaller(jaxbContext);

            // Variable to ensure whether provided InputStream is EPCIS document or single event
            boolean isDocument = false;

            // Clear the namespaces before reading the document
            namespaceResolver.resetAllNamespaces();

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

            // Navigate to next and start of the XML Elements
            xmlStreamReader.next();

            // Read Until the end of the file and unmarshall event-by-event
            while (xmlStreamReader.hasNext()) {

                // Check if the initial element is one of the elements from "EVENT_TYPES" (one of EPCIS
                // event)
                if (xmlStreamReader.isStartElement()
                        && EPCIS.EPCIS_EVENT_TYPES.contains(xmlStreamReader.getLocalName())) {

                    // Get the event type
                    Object event = getEvent(xmlStreamReader, unmarshaller);

                    // Check if Object has some value
                    if (event != null) {
                        // map event
                        event = applyEventMapper(sequenceInEventList, event);

                        // StringWriter to get the converted XML from marshaller
                        final StringWriter singleXmlEvent = new StringWriter();

                        // Set the namespaces for the marshaller
                        marshaller.setProperty(
                                MarshallerProperties.NAMESPACE_PREFIX_MAPPER,
                                namespaceResolver.getAllNamespaces());

                        final XMLStreamWriter skipEPCISNamespaceWriter =
                                new NonEPCISNamespaceXMLStreamWriter(
                                        new IndentingXMLStreamWriter(
                                                XML_OUTPUT_FACTORY.createXMLStreamWriter(singleXmlEvent)));

                        // Marshaller properties: Add the custom namespaces instead of the ns1, ns2
                        marshaller.marshal(event, skipEPCISNamespaceWriter);

                        // If the provided XML is EPCIS document then add the converted event to Collectors List
                        // and proceed to next event
                        if (isDocument) {
                            // Call the method to check if the event adheres to JSON-Schema or write into the
                            // OutputStream using the EventHandler
                            eventHandler.handler(singleXmlEvent);
                        } else {
                            // If the provided XML is Single EPCIS event then convert it and add to collector and
                            // End the execution.
                            eventHandler.startSingleEvent(contextAttributes);
                            eventHandler.collectSingleEvent(singleXmlEvent);
                            eventHandler.endSingleEvent();
                            return;
                        }
                    }

                } else if (xmlStreamReader.isStartElement()) {

                    // For EPCISQueryDocument set SubscriptionID and QueryName for XML writing
                    setSubScriptionIdAndQueryName(eventHandler, contextAttributes, xmlStreamReader);

                    if (xmlStreamReader.getLocalName().toLowerCase().contains(EPCIS.DOCUMENT)) {
                        // Get the information related to the XML header elements till "EventList", If the
                        // element is EPCISDocument get all namespaces

                        // Set the variable to true if the provided XML is EPCIS document else set to false for
                        // single EPCIS event
                        isDocument = true;

                        // Set for EPCISDocument or EPCISQueryDocument for adding the header elements
                        eventHandler.setIsEPCISDocument(
                                xmlStreamReader.getLocalName().equalsIgnoreCase(EPCIS.EPCIS_DOCUMENT));

                        // Get all Namespaces from the XML header and store it within the xmlNamespaces MAP
                        prepareNameSpaces(xmlStreamReader);

                        // Get all the Attributes from XML header and store it within attributes MAP for
                        // creation of final JSON
                        prepareContextAttributes(contextAttributes, xmlStreamReader);

                        // For EPCISDocument invoke EventHandle Start to create the header information at
                        // EPCISDocument
                        if (xmlStreamReader.getLocalName().equalsIgnoreCase(EPCIS.EPCIS_DOCUMENT)) {
                            eventHandler.start(contextAttributes);
                        }
                    }
                }
                // Move to the next event/element in InputStream
                xmlStreamReader.next();
            }

            // Call the EventHandle End method to end all the header objects created in the Start method
            eventHandler.end();

        } catch (Exception e) {
            throw new FormatConverterException("XML to JSON/JSON-LD conversion failed, " + e.getMessage());
        }

    }
    public XMLEventValueTransformer mapWith(Function<Object, Object> mapper) {
        return new XMLEventValueTransformer(this, mapper);
    }

}
