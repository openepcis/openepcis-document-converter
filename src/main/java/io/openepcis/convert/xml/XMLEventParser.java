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
import io.openepcis.convert.collector.EPCISEventCollector;
import io.openepcis.convert.collector.EventHandler;
import io.openepcis.convert.exception.FormatConverterException;
import io.openepcis.model.epcis.*;
import io.openepcis.model.epcis.util.DefaultJsonSchemaNamespaceURIResolver;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.openepcis.constants.EPCIS.PROTECTED_TERMS_OF_CONTEXT;

@Slf4j
public abstract class XMLEventParser {

    protected final JAXBContext jaxbContext;

    protected final DefaultJsonSchemaNamespaceURIResolver namespaceResolver =
            DefaultJsonSchemaNamespaceURIResolver.getContext();

    protected Optional<Function<Object, Object>> epcisEventMapper = Optional.empty();

    protected static final XMLOutputFactory XML_OUTPUT_FACTORY = XMLOutputFactory.newInstance();


    public XMLEventParser(JAXBContext jaxbContext) {
        this.jaxbContext = jaxbContext;
    }

    protected void validateXmlEvent(Unmarshaller unmarshaller) throws JAXBException {
        unmarshaller.setEventHandler(
                validationEvent -> {
                    throw new FormatConverterException(validationEvent.getMessage());
                });
    }

    protected XMLStreamReader createXmlStreamReader(InputStream xmlStream) throws XMLStreamException {
        final XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        final XMLStreamReader xmlStreamReader = inputFactory.createXMLStreamReader(xmlStream);
        return xmlStreamReader;
    }

    protected Marshaller createMashaller(JAXBContext jaxbContext) throws JAXBException {
        // Create a Marshaller instance to convert to XML
        final Marshaller marshaller = jaxbContext.createMarshaller();
        // Set Marshalling properties: print formatted XML, exclude <xml> version tag for every event,
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        return marshaller;
    }

    protected void validateXmlStrem(InputStream xmlStream) {
        if (xmlStream == null) {
            throw new FormatConverterException(
                    "Unable to convert the events from XML - JSON-LD as InputStream contains NULL values");
        }
    }

    protected Object getEvent(XMLStreamReader xmlStreamReader, Unmarshaller unmarshaller) throws JAXBException {
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
        return event;
    }

    protected Object applyEventMapper(AtomicInteger sequenceInEventList, Object event) {
        if (epcisEventMapper.isPresent() && EPCISEvent.class.isAssignableFrom(event.getClass())) {
            final EPCISEvent ev = (EPCISEvent) event;
            //Change the key value to keep key as localname and value as namespaceURI
            final Map<String, String> swappedMap = namespaceResolver.getAllNamespaces().entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
            ev.setContextInfo(List.of(swappedMap));
            ev.setSequenceInEPCISDoc(sequenceInEventList.incrementAndGet());

            event = epcisEventMapper.get().apply(event);
        }
        return event;
    }


    protected void setSubScriptionIdAndQueryName(EventHandler<? extends EPCISEventCollector> eventHandler, Map<String, String> contextAttributes, XMLStreamReader xmlStreamReader) throws XMLStreamException {
        if (!eventHandler.isEPCISDocument()) {
            if (xmlStreamReader.getLocalName().equalsIgnoreCase(EPCIS.SUBSCRIPTION_ID)) {
                eventHandler.setSubscriptionID(xmlStreamReader.getElementText());
            } else if (xmlStreamReader.getLocalName().equalsIgnoreCase(EPCIS.QUERY_NAME)) {
                eventHandler.setQueryName(xmlStreamReader.getElementText());
            } else if (xmlStreamReader
                    .getLocalName()
                    .equalsIgnoreCase(EPCIS.RESULTS_BODY_IN_CAMEL_CASE)) {
                // For QueryDocument invoke EventHandle Start to create the header information at
                // resultsBody
                eventHandler.start(contextAttributes);
            }
        }
    }

    protected void prepareNameSpaces(XMLStreamReader xmlStreamReader) {
        IntStream.range(0, xmlStreamReader.getNamespaceCount())
                .forEach(
                        namespaceIndex -> {
                            // Omit the Namespace values which are already present within JSON-LD Schema
                            // by
                            // default
                            if (!PROTECTED_TERMS_OF_CONTEXT.contains(
                                    xmlStreamReader.getNamespacePrefix(namespaceIndex))) {
                                namespaceResolver.populateDocumentNamespaces(
                                        xmlStreamReader.getNamespaceURI(namespaceIndex),
                                        xmlStreamReader.getNamespacePrefix(namespaceIndex));
                            }
                        });
    }

    protected void prepareContextAttributes(Map<String, String> contextAttributes, XMLStreamReader xmlStreamReader) {
        IntStream.range(0, xmlStreamReader.getAttributeCount())
                .forEach(
                        attributeIndex -> {
                            // Omit the attribute values which are already present within JSON-LD Schema
                            // by
                            // default
                            if (!PROTECTED_TERMS_OF_CONTEXT.contains(
                                    xmlStreamReader.getAttributeName(attributeIndex))) {
                                contextAttributes.put(
                                        String.valueOf(xmlStreamReader.getAttributeName(attributeIndex)),
                                        xmlStreamReader.getAttributeValue(attributeIndex));
                            }
                        });
    }

}
