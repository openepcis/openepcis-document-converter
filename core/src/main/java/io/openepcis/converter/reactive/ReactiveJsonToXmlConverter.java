/*
 * Copyright 2022-2025 benelog GmbH & Co. KG
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
package io.openepcis.converter.reactive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openepcis.constants.EPCIS;
import io.openepcis.converter.collector.context.ContextProcessor;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.converter.util.IndentingXMLStreamWriter;
import io.openepcis.converter.util.NonEPCISNamespaceXMLStreamWriter;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.modifier.CustomExtensionAdapter;
import io.openepcis.model.epcis.util.ConversionNamespaceContext;
import io.openepcis.model.epcis.util.EPCISNamespacePrefixMapper;
import io.openepcis.reactive.publisher.ObjectNodePublisher;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.util.EventReaderDelegate;
import org.eclipse.persistence.jaxb.JAXBContextProperties;
import org.eclipse.persistence.jaxb.MarshallerProperties;

/**
 * Reactive JSON-LD to XML converter using non-blocking JSON parsing with backpressure support.
 *
 * <p>This converter uses {@link ObjectNodePublisher} from openepcis-reactive-event-publisher
 * for non-blocking JSON parsing, then marshals each event to XML using JAXB.
 *
 * <p><strong>Architecture:</strong>
 * <pre>
 * Flow.Publisher&lt;ByteBuffer&gt; (JSON input)
 *         |
 *         v
 * ObjectNodePublisher (non-blocking JSON parsing)
 *         |
 *         v
 * Multi&lt;ObjectNode&gt; (header + events)
 *         |
 *         v
 * JAXB Marshalling (ObjectNode -&gt; EPCISEvent -&gt; XML bytes)
 *         |
 *         v
 * Multi&lt;byte[]&gt; (XML output - truly streaming)
 * </pre>
 *
 * <p><strong>Memory:</strong> O(1) per event - only one event in memory at a time.
 * GB-sized files with millions of events can be processed without OutOfMemoryError.
 *
 * <p><strong>Backpressure:</strong> Fully supported. JSON parsing only proceeds when
 * downstream has demand for more events.
 *
 * <p><strong>Thread safety:</strong> Instances are NOT thread-safe. Create one instance per
 * conversion operation.
 */
public class ReactiveJsonToXmlConverter {

  private static final XMLOutputFactory XML_OUTPUT_FACTORY = XMLOutputFactory.newInstance();
  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

  static {
    XML_INPUT_FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
  }

  private final JAXBContext jaxbContext;
  private final ObjectMapper objectMapper;
  private final Optional<BiFunction<Object, List<Object>, Object>> eventMapper;

  /**
   * Creates a new converter with default JAXB context.
   *
   * @throws JAXBException if JAXB context creation fails
   */
  public ReactiveJsonToXmlConverter() throws JAXBException {
    this(createDefaultJAXBContext(), Optional.empty());
  }

  /**
   * Creates a new converter with specified JAXB context.
   *
   * @param jaxbContext the JAXB context for marshalling
   */
  public ReactiveJsonToXmlConverter(JAXBContext jaxbContext) {
    this(jaxbContext, Optional.empty());
  }

  /**
   * Creates a new converter with specified JAXB context and event mapper.
   *
   * @param jaxbContext the JAXB context for marshalling
   * @param eventMapper optional function to transform events before marshalling
   */
  public ReactiveJsonToXmlConverter(
      JAXBContext jaxbContext,
      Optional<BiFunction<Object, List<Object>, Object>> eventMapper) {
    this.jaxbContext = Objects.requireNonNull(jaxbContext, "JAXBContext cannot be null");
    this.eventMapper = eventMapper;
    this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  private static JAXBContext createDefaultJAXBContext() throws JAXBException {
    return JAXBContext.newInstance(
        "io.openepcis.model.epcis",
        Thread.currentThread().getContextClassLoader(),
        new HashMap<>() {{
          put(JAXBContextProperties.NAMESPACE_PREFIX_MAPPER, new EPCISNamespacePrefixMapper());
        }});
  }

  /**
   * Converts JSON-LD input to XML byte stream.
   *
   * @param source the reactive JSON input source
   * @return Multi emitting XML byte chunks
   */
  public Multi<byte[]> convert(Flow.Publisher<ByteBuffer> source) {
    return convert(ReactiveConversionSource.fromPublisher(source));
  }

  /**
   * Converts JSON-LD input to XML byte stream using truly streaming approach.
   *
   * <p>Memory usage is O(1) per event - only one event is held in memory at a time.
   * This allows processing of arbitrarily large files (GB-sized with millions of events)
   * without running out of memory.
   *
   * @param source the conversion source
   * @return Multi emitting XML byte chunks
   */
  public Multi<byte[]> convert(ReactiveConversionSource source) {
    try {
      ObjectNodePublisher<ObjectNode> publisher = createPublisher(source);

      // Document-level namespace context - populated from header, shared by all events
      final ConversionNamespaceContext documentNsContext = new ConversionNamespaceContext();

      // State tracking for streaming
      final AtomicBoolean headerEmitted = new AtomicBoolean(false);
      final AtomicInteger sequence = new AtomicInteger(0);
      final AtomicReference<Boolean> isEPCISDocument = new AtomicReference<>(true);

      return Multi.createFrom().publisher(publisher)
          .onItem().transformToMultiAndConcatenate(node -> {
            // First node with header characteristics - emit XML header
            if (!headerEmitted.get() && isHeaderNode(node)) {
              headerEmitted.set(true);

              // Extract document-level namespaces
              extractNamespacesToContext(node, documentNsContext);

              // Determine document type
              String type = node.has("type") ? node.get("type").asText() : "";
              isEPCISDocument.set(!type.contains("QueryDocument"));

              // Extract query document metadata
              String subscriptionID = node.has("subscriptionID") ? node.get("subscriptionID").asText() : null;
              String queryName = node.has("queryName") ? node.get("queryName").asText() : null;

              try {
                byte[] headerBytes = createXmlHeader(node, documentNsContext, isEPCISDocument.get(), subscriptionID, queryName);
                return Multi.createFrom().item(headerBytes);
              } catch (Exception e) {
                return Multi.createFrom().failure(
                    new FormatConverterException("Failed to create XML header: " + e.getMessage(), e));
              }
            }

            // Event node - marshal and emit
            if (!isHeaderNode(node) || headerEmitted.get()) {
              try {
                // Create event-scoped namespace context with document namespaces + this event's namespaces
                ConversionNamespaceContext eventScopedContext = ConversionNamespaceContext.createEventScoped(documentNsContext);
                if (node.has("@context")) {
                  extractNamespacesToContext(node, eventScopedContext);
                }

                byte[] eventBytes = marshalEvent(node, eventScopedContext, sequence);
                if (eventBytes != null && eventBytes.length > 0) {
                  return Multi.createFrom().item(eventBytes);
                }
                return Multi.createFrom().empty();
              } catch (Exception e) {
                return Multi.createFrom().failure(
                    new FormatConverterException("Failed to marshal event: " + e.getMessage(), e));
              }
            }

            return Multi.createFrom().empty();
          })
          .onCompletion().continueWith(() -> {
            // Emit footer as a single-element list
            return List.of(createXmlFooter(isEPCISDocument.get()));
          });

    } catch (IOException e) {
      return Multi.createFrom().failure(
          new FormatConverterException("Failed to initialize JSON parser: " + e.getMessage(), e));
    }
  }

  /**
   * Creates XML header bytes using XMLEventWriter.
   */
  private byte[] createXmlHeader(ObjectNode headerNode, ConversionNamespaceContext nsContext,
                                  boolean isEPCISDocument, String subscriptionID, String queryName)
      throws XMLStreamException {

    ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
    XMLEventWriter xmlEventWriter = XML_OUTPUT_FACTORY.createXMLEventWriter(baos, StandardCharsets.UTF_8.name());
    XMLEventFactory events = XMLEventFactory.newInstance();

    try {
      // Start document
      xmlEventWriter.add(events.createStartDocument(StandardCharsets.UTF_8.name(), "1.0"));

      // Start EPCISDocument or EPCISQueryDocument
      xmlEventWriter.add(
          events.createStartElement(
              new QName(
                  isEPCISDocument
                      ? EPCIS.EPCIS_DOCUMENT_WITH_NAMESPACE
                      : EPCIS.EPCIS_QUERY_DOCUMENT_WITH_NAMESPACE),
              null,
              null));

      // Add EPCIS namespace
      xmlEventWriter.add(
          isEPCISDocument
              ? events.createNamespace(EPCIS.EPCIS, EPCIS.EPCIS_2_0_XMLNS)
              : events.createNamespace(EPCIS.EPCIS_QUERY, EPCIS.EPCIS_QUERY_2_0_XMLNS));

      // Add XSI namespace
      xmlEventWriter.add(events.createNamespace(EPCIS.XSI, EPCIS.XML_SCHEMA_INSTANCE));

      // Add CBV MDA namespace only if it's used in the @context
      if (hasCbvmdaInContext(headerNode)) {
        xmlEventWriter.add(events.createNamespace("cbvmda", "urn:epcglobal:cbv:mda"));
      }

      // Add custom namespaces from document context only
      for (Map.Entry<String, String> ns : nsContext.getNamespacesForXml().entrySet()) {
        String prefix = ns.getKey();
        String uri = ns.getValue();
        if (isValidCustomNamespacePrefix(prefix)) {
          xmlEventWriter.add(events.createNamespace(prefix, uri));
        }
      }

      // Add schemaVersion attribute
      String schemaVersion = "2.0";
      if (headerNode != null && headerNode.has("schemaVersion")) {
        schemaVersion = headerNode.get("schemaVersion").asText();
      }
      xmlEventWriter.add(events.createAttribute("schemaVersion", schemaVersion));

      // Add creationDate attribute
      if (headerNode != null && headerNode.has("creationDate")) {
        xmlEventWriter.add(events.createAttribute("creationDate", headerNode.get("creationDate").asText()));
      }

      // Start EPCISBody
      xmlEventWriter.add(events.createStartElement(new QName(EPCIS.EPCIS_BODY), null, null));

      // Add additional wrapper tags for EPCISQueryDocument
      if (!isEPCISDocument) {
        xmlEventWriter.add(events.createStartElement(new QName(EPCIS.QUERY_RESULTS), null, null));

        if (subscriptionID != null && !subscriptionID.isBlank()) {
          xmlEventWriter.add(events.createStartElement(new QName(EPCIS.SUBSCRIPTION_ID), null, null));
          xmlEventWriter.add(events.createCharacters(subscriptionID));
          xmlEventWriter.add(events.createEndElement(new QName(EPCIS.SUBSCRIPTION_ID), null));
        }

        if (queryName != null && !queryName.isBlank()) {
          xmlEventWriter.add(events.createStartElement(new QName(EPCIS.QUERY_NAME), null, null));
          xmlEventWriter.add(events.createCharacters(queryName));
          xmlEventWriter.add(events.createEndElement(new QName(EPCIS.QUERY_NAME), null));
        }

        xmlEventWriter.add(events.createStartElement(new QName(EPCIS.RESULTS_BODY_IN_CAMEL_CASE), null, null));
      }

      // Start EventList
      xmlEventWriter.add(events.createStartElement(new QName(EPCIS.EVENT_LIST), null, null));

      // Add empty characters to force the closing '>' of EventList start element to be written
      xmlEventWriter.add(events.createCharacters(""));

      xmlEventWriter.flush();
      return baos.toByteArray();

    } finally {
      xmlEventWriter.close();
    }
  }

  /**
   * Marshals a single event node to XML bytes.
   */
  private byte[] marshalEvent(ObjectNode eventNode, ConversionNamespaceContext eventScopedContext,
                               AtomicInteger sequence) throws JAXBException, XMLStreamException {

    EPCISEvent event = nodeToEvent(eventNode, eventScopedContext);
    if (event == null) {
      return null;
    }

    event.getOpenEPCISExtension().setSequenceInEPCISDoc(sequence.incrementAndGet());
    event.getOpenEPCISExtension().setConversionNamespaceContext(eventScopedContext);

    // Apply event mapper if present
    Map<String, String> swappedMap = eventScopedContext.getAllNamespaces().entrySet().stream()
        .collect(java.util.stream.Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    Object mappedEvent = eventMapper
        .map(mapper -> mapper.apply(event, List.of(swappedMap)))
        .orElse(event);

    if (!(mappedEvent instanceof EPCISEvent)) {
      return null;
    }

    // Create marshaller with event-scoped namespace mapper
    Marshaller marshaller = jaxbContext.createMarshaller();
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
    marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
    marshaller.setProperty(MarshallerProperties.NAMESPACE_PREFIX_MAPPER, eventScopedContext.getAllNamespaces());
    marshaller.setAdapter(CustomExtensionAdapter.class, new CustomExtensionAdapter(eventScopedContext));

    // Marshal to string
    StringWriter singleXmlEvent = new StringWriter();
    XMLStreamWriter skipEPCISNamespaceWriter =
        new NonEPCISNamespaceXMLStreamWriter(
            new IndentingXMLStreamWriter(
                XML_OUTPUT_FACTORY.createXMLStreamWriter(singleXmlEvent)));
    marshaller.marshal(mappedEvent, skipEPCISNamespaceWriter);
    skipEPCISNamespaceWriter.flush();

    // Collect event XML using XMLEventWriter for proper formatting
    ByteArrayOutputStream baos = new ByteArrayOutputStream(2048);
    XMLEventWriter xmlEventWriter = XML_OUTPUT_FACTORY.createXMLEventWriter(baos, StandardCharsets.UTF_8.name());

    try {
      collectEvent(xmlEventWriter, singleXmlEvent.toString());
      xmlEventWriter.flush();
      return baos.toByteArray();
    } finally {
      xmlEventWriter.close();
    }
  }

  /**
   * Creates XML footer bytes.
   */
  private byte[] createXmlFooter(boolean isEPCISDocument) {
    StringBuilder footer = new StringBuilder();
    footer.append("\n</EventList>\n");

    if (!isEPCISDocument) {
      footer.append("</resultsBody>\n");
      footer.append("</QueryResults>\n");
    }

    footer.append("</EPCISBody>\n");
    footer.append(isEPCISDocument ? "</epcis:EPCISDocument>" : "</epcisq:EPCISQueryDocument>");

    return footer.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Collects an event by parsing its XML and adding to the XMLEventWriter.
   */
  private void collectEvent(XMLEventWriter xmlEventWriter, String eventXml) throws XMLStreamException {
    XMLEventReader xer =
        new EventReaderDelegate(
            XML_INPUT_FACTORY.createXMLEventReader(new StringReader(eventXml))) {
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
      xer.nextEvent(); // Skip StartDocument
      xmlEventWriter.add(xer);
    }
  }

  /**
   * Checks if the prefix is a valid custom namespace prefix.
   */
  private boolean isValidCustomNamespacePrefix(String prefix) {
    return prefix != null && !prefix.isEmpty()
        && !prefix.startsWith("xmlns")
        && !prefix.contains(":")
        && !prefix.contains("/")
        && !prefix.startsWith("http")
        && !EPCIS.EPCIS_DEFAULT_NAMESPACES.containsKey(prefix)
        && !EPCIS.XSI.equals(prefix)
        && !EPCIS.EPCIS.equals(prefix)
        && !EPCIS.EPCIS_QUERY.equals(prefix)
        && !"cbvmda".equals(prefix);
  }

  /**
   * Extracts namespaces from @context into the conversion namespace context.
   */
  private void extractNamespacesToContext(ObjectNode node, ConversionNamespaceContext nsContext) {
    JsonNode contextNode = node.get("@context");
    if (contextNode == null) {
      return;
    }

    ContextProcessor contextProcessor = ContextProcessor.getInstance();

    if (contextNode.isArray()) {
      for (JsonNode item : contextNode) {
        if (item.isTextual()) {
          String contextUrl = item.asText();
          try {
            contextProcessor.resolveForXmlConversion(
                Map.of(contextUrl, contextUrl), nsContext);
          } catch (Exception e) {
            // Handler not found for this URL - continue
          }
        } else if (item.isObject()) {
          Iterator<Map.Entry<String, JsonNode>> fields = item.fields();
          while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getValue().isTextual()) {
              nsContext.populateDocumentNamespaces(entry.getValue().asText(), entry.getKey());
            }
          }
        }
      }
    } else if (contextNode.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = contextNode.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        if (entry.getValue().isTextual()) {
          nsContext.populateDocumentNamespaces(entry.getValue().asText(), entry.getKey());
        }
      }
    }
  }

  /**
   * Converts JSON-LD input to EPCISEvent stream.
   *
   * @param source the reactive JSON input source
   * @return Multi emitting EPCISEvent objects
   */
  public Multi<EPCISEvent> convertToEvents(Flow.Publisher<ByteBuffer> source) {
    return convertToEvents(ReactiveConversionSource.fromPublisher(source));
  }

  /**
   * Converts JSON-LD input to EPCISEvent stream.
   *
   * @param source the conversion source
   * @return Multi emitting EPCISEvent objects
   */
  public Multi<EPCISEvent> convertToEvents(ReactiveConversionSource source) {
    try {
      ObjectNodePublisher<ObjectNode> publisher = createPublisher(source);

      final ConversionNamespaceContext nsContext = new ConversionNamespaceContext();
      AtomicBoolean headerSkipped = new AtomicBoolean(false);

      return Multi.createFrom().publisher(publisher)
          .filter(node -> {
            if (!headerSkipped.get() && isHeaderNode(node)) {
              headerSkipped.set(true);
              extractNamespacesToContext(node, nsContext);
              return false;
            }
            if (node.has("@context")) {
              extractNamespacesToContext(node, nsContext);
            }
            return true;
          })
          .onItem().transform(node -> nodeToEvent(node, nsContext))
          .filter(Objects::nonNull);

    } catch (IOException e) {
      return Multi.createFrom().failure(
          new FormatConverterException("Failed to initialize JSON parser: " + e.getMessage(), e));
    }
  }

  /**
   * Creates a new converter with event mapper applied.
   *
   * @param mapper function to transform events
   * @return new converter instance with mapper
   */
  public ReactiveJsonToXmlConverter mapWith(BiFunction<Object, List<Object>, Object> mapper) {
    return new ReactiveJsonToXmlConverter(this.jaxbContext, Optional.ofNullable(mapper));
  }

  // ==================== Internal Methods ====================

  private ObjectNodePublisher<ObjectNode> createPublisher(ReactiveConversionSource source)
      throws IOException {
    if (source.hasRetrySupport()) {
      return new ObjectNodePublisher<>(source.toPublisher(), source.retrySource());
    }
    return new ObjectNodePublisher<>(source.toPublisher());
  }

  private boolean isHeaderNode(ObjectNode node) {
    if (node.has("type")) {
      String type = node.get("type").asText();
      if (type.contains("EPCISDocument") || type.contains("EPCISQueryDocument")) {
        return true;
      }
    }
    return node.has("@context") && node.has("schemaVersion");
  }

  private EPCISEvent nodeToEvent(ObjectNode node, ConversionNamespaceContext nsContext) {
    try {
      if (!node.has("type")) {
        return null;
      }

      String type = node.get("type").asText();
      if (!isEventType(type)) {
        return null;
      }

      ObjectReader reader = objectMapper.readerFor(EPCISEvent.class)
          .withAttribute(ConversionNamespaceContext.ATTR_KEY, nsContext);
      return reader.readValue(node);
    } catch (Exception e) {
      throw new FormatConverterException("Failed to convert node to EPCISEvent: " + e.getMessage(), e);
    }
  }

  private boolean isEventType(String type) {
    return "ObjectEvent".equals(type) ||
        "AggregationEvent".equals(type) ||
        "TransactionEvent".equals(type) ||
        "TransformationEvent".equals(type) ||
        "AssociationEvent".equals(type);
  }

  /**
   * Checks if cbvmda namespace is present in the JSON @context.
   *
   * @param headerNode the document header node
   * @return true if cbvmda is referenced in @context
   */
  private boolean hasCbvmdaInContext(ObjectNode headerNode) {
    if (headerNode == null) {
      return false;
    }
    JsonNode contextNode = headerNode.get("@context");
    if (contextNode == null) {
      return false;
    }
    String contextStr = contextNode.toString();
    return contextStr.contains("cbvmda") || contextStr.contains("urn:epcglobal:cbv:mda");
  }
}
