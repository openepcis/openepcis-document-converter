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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openepcis.constants.EPCIS;
import io.openepcis.converter.collector.context.ContextProcessor;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.converter.util.IndentingXMLStreamWriter;
import io.openepcis.converter.util.NonEPCISNamespaceXMLStreamWriter;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.util.ConversionNamespaceContext;
import io.openepcis.model.epcis.util.EPCISNamespacePrefixMapper;
import io.openepcis.reactive.publisher.ObjectNodePublisher;
import io.smallrye.mutiny.Multi;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
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
 * Multi&lt;byte[]&gt; (XML output in 8KB chunks)
 * </pre>
 *
 * <p><strong>Backpressure:</strong> Fully supported. JSON parsing only proceeds when
 * downstream has demand for more events.
 *
 * <p><strong>Thread safety:</strong> Instances are NOT thread-safe. Create one instance per
 * conversion operation.
 */
public class ReactiveJsonToXmlConverter {

  private static final XMLOutputFactory XML_OUTPUT_FACTORY = XMLOutputFactory.newInstance();
  private static final int DEFAULT_BUFFER_SIZE = 8192;

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
   * Converts JSON-LD input to XML byte stream.
   *
   * @param source the conversion source
   * @return Multi emitting XML byte chunks
   */
  public Multi<byte[]> convert(ReactiveConversionSource source) {
    try {
      ObjectNodePublisher<ObjectNode> publisher = createPublisher(source);

      // Two-pass approach: First collect all nodes and namespaces, then emit XML
      // This is necessary because event-level @context may define namespaces used in events,
      // but we need all namespaces declared in the XML header

      // Create conversion-scoped namespace context - captured by lambdas below
      // This replaces the ThreadLocal singleton to avoid thread-switching issues
      final ConversionNamespaceContext nsContext = new ConversionNamespaceContext();

      return Multi.createFrom().publisher(publisher)
          .collect().asList()
          .onItem().transformToMulti(nodes -> {
            if (nodes.isEmpty()) {
              return Multi.createFrom().empty();
            }

            // First pass: Extract namespaces from ALL nodes (header + events) into scoped context
            ObjectNode headerNode = null;
            List<ObjectNode> eventNodes = new ArrayList<>();

            for (ObjectNode node : nodes) {
              if (headerNode == null && isHeaderNode(node)) {
                headerNode = node;
                extractNamespacesToContext(node, nsContext);
              } else {
                // Extract event-level namespaces
                if (node.has("@context")) {
                  extractNamespacesToContext(node, nsContext);
                }
                eventNodes.add(node);
              }
            }

            // Second pass: Emit XML with all namespaces now known
            return emitXmlWithAllNamespaces(headerNode, eventNodes, nsContext);
          });

    } catch (IOException e) {
      return Multi.createFrom().failure(
          new FormatConverterException("Failed to initialize JSON parser", e));
    }
  }

  /**
   * Emits XML output with all namespaces declared in the header.
   */
  private Multi<byte[]> emitXmlWithAllNamespaces(ObjectNode headerNode, List<ObjectNode> eventNodes,
                                                  ConversionNamespaceContext nsContext) {
    return Multi.createFrom().emitter(emitter -> {
      try {
        // Emit header with all collected namespaces
        if (headerNode != null) {
          byte[] header = buildXmlHeaderWithNamespaces(headerNode, nsContext);
          emitter.emit(header);
        }

        // Emit events
        AtomicInteger sequence = new AtomicInteger(0);
        for (ObjectNode eventNode : eventNodes) {
          EPCISEvent event = nodeToEvent(eventNode);
          if (event != null) {
            event.getOpenEPCISExtension().setSequenceInEPCISDoc(sequence.incrementAndGet());

            // Create swapped map for mapper (prefix -> URI format)
            Map<String, String> swappedMap = nsContext.getAllNamespaces().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
            Object mappedEvent = eventMapper
                .map(mapper -> mapper.apply(event, List.of(swappedMap)))
                .orElse(event);

            if (mappedEvent instanceof EPCISEvent) {
              byte[] xmlBytes = marshalEvent((EPCISEvent) mappedEvent, nsContext);
              if (xmlBytes.length > 0) {
                emitter.emit(xmlBytes);
              }
            }
          }
        }

        // Emit footer
        emitter.emit(getXmlFooter());
        emitter.complete();

      } catch (Exception e) {
        emitter.fail(new FormatConverterException("Failed to convert JSON to XML", e));
      }
    });
  }

  /**
   * Builds XML header with all collected namespaces.
   */
  private byte[] buildXmlHeaderWithNamespaces(ObjectNode headerNode, ConversionNamespaceContext nsContext)
      throws XMLStreamException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
    XMLStreamWriter writer = createXmlWriter(baos, false);
    writeXmlHeader(writer, headerNode, nsContext);
    writer.flush();
    return baos.toByteArray();
  }

  /**
   * Extracts namespaces from @context into the conversion namespace context.
   * Stores as URI -> prefix format (used by ConversionNamespaceContext).
   * Also resolves external context URLs via SPI-loaded ContextHandlers (e.g., GS1 Egypt).
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
          // String URL in @context (e.g., GS1 Egypt context URL)
          // Resolve via ContextProcessor to trigger SPI handlers
          String contextUrl = item.asText();
          try {
            contextProcessor.resolveForXmlConversion(
                Map.of(contextUrl, contextUrl), nsContext);
          } catch (Exception e) {
            // Handler not found for this URL - that's okay, continue
          }
        } else if (item.isObject()) {
          Iterator<Map.Entry<String, JsonNode>> fields = item.fields();
          while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getValue().isTextual()) {
              // Store as URI -> prefix (ConversionNamespaceContext format)
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
   * <p>This provides event-level backpressure - each event is only parsed and
   * converted when downstream requests it.
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

      AtomicBoolean headerSkipped = new AtomicBoolean(false);

      return Multi.createFrom().publisher(publisher)
          .filter(node -> {
            // Skip header node (first node with document metadata)
            if (!headerSkipped.get() && isHeaderNode(node)) {
              headerSkipped.set(true);
              return false;
            }
            return true;
          })
          .onItem().transform(this::nodeToEvent)
          .filter(Objects::nonNull);

    } catch (IOException e) {
      return Multi.createFrom().failure(
          new FormatConverterException("Failed to initialize JSON parser", e));
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
    // Header node has type=EPCISDocument/EPCISQueryDocument or @context at document level
    // Note: Events may also have @context, but we use state tracking in convert() to ensure
    // only the first header node is processed as a header
    if (node.has("type")) {
      String type = node.get("type").asText();
      if (type.contains("EPCISDocument") || type.contains("EPCISQueryDocument")) {
        return true;
      }
    }
    // First node with @context is typically the document header
    return node.has("@context") && node.has("schemaVersion");
  }

  private EPCISEvent nodeToEvent(ObjectNode node) {
    try {
      // Check if it's an event node (has type field with event type)
      if (!node.has("type")) {
        return null;
      }

      String type = node.get("type").asText();
      if (!isEventType(type)) {
        return null;
      }

      return objectMapper.treeToValue(node, EPCISEvent.class);
    } catch (Exception e) {
      throw new FormatConverterException("Failed to convert node to EPCISEvent", e);
    }
  }

  private boolean isEventType(String type) {
    return "ObjectEvent".equals(type) ||
        "AggregationEvent".equals(type) ||
        "TransactionEvent".equals(type) ||
        "TransformationEvent".equals(type) ||
        "AssociationEvent".equals(type);
  }

  private XMLStreamWriter createXmlWriter(ByteArrayOutputStream baos, boolean skipEpcisNamespace)
      throws XMLStreamException {
    XMLStreamWriter writer = new IndentingXMLStreamWriter(
        XML_OUTPUT_FACTORY.createXMLStreamWriter(baos, StandardCharsets.UTF_8.name()));
    // Only wrap with NonEPCISNamespaceXMLStreamWriter for event fragments, not for header
    return skipEpcisNamespace ? new NonEPCISNamespaceXMLStreamWriter(writer) : writer;
  }

  private void writeXmlHeader(XMLStreamWriter writer, ObjectNode headerNode,
                              ConversionNamespaceContext nsContext) throws XMLStreamException {
    writer.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");

    // Write EPCISDocument root element with namespaces
    writer.writeStartElement("epcis", "EPCISDocument",
        "urn:epcglobal:epcis:xsd:2");

    // Write standard namespaces
    writer.writeNamespace("epcis", "urn:epcglobal:epcis:xsd:2");
    writer.writeNamespace("cbvmda", "urn:epcglobal:cbv:mda");
    writer.writeNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");

    // Write custom namespaces from context (getNamespacesForXml returns prefix -> URI)
    for (Map.Entry<String, String> ns : nsContext.getNamespacesForXml().entrySet()) {
      String prefix = ns.getKey();
      String uri = ns.getValue();
      // Skip invalid prefixes: must be valid XML NCName (no colons, slashes, etc.)
      // and not start with "xmlns" or be a URL
      // Also skip standard EPCIS namespaces (already written above) and xsi
      if (prefix != null && !prefix.isEmpty()
          && !prefix.startsWith("xmlns")
          && !prefix.contains(":")
          && !prefix.contains("/")
          && !prefix.startsWith("http")
          && !EPCIS.EPCIS_DEFAULT_NAMESPACES.containsKey(prefix)
          && !EPCIS.XSI.equals(prefix)) {
        writer.writeNamespace(prefix, uri);
      }
    }

    // Write schemaVersion attribute
    if (headerNode.has("schemaVersion")) {
      writer.writeAttribute("schemaVersion", headerNode.get("schemaVersion").asText());
    } else {
      writer.writeAttribute("schemaVersion", "2.0");
    }

    // Write creationDate attribute
    if (headerNode.has("creationDate")) {
      writer.writeAttribute("creationDate", headerNode.get("creationDate").asText());
    }

    // Write EPCISBody and eventList opening tags
    writer.writeStartElement("EPCISBody");
    writer.writeStartElement("EventList");
    // Force the closing '>' to be written by writing empty characters
    writer.writeCharacters("");
  }

  private byte[] marshalEvent(EPCISEvent event, ConversionNamespaceContext nsContext)
      throws JAXBException, XMLStreamException {
    Marshaller marshaller = jaxbContext.createMarshaller();
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
    marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
    // getAllNamespaces() returns URI->prefix map which is what JAXB expects
    marshaller.setProperty(MarshallerProperties.NAMESPACE_PREFIX_MAPPER, nsContext.getAllNamespaces());

    StringWriter stringWriter = new StringWriter();
    XMLStreamWriter xmlWriter = new NonEPCISNamespaceXMLStreamWriter(
        new IndentingXMLStreamWriter(
            XML_OUTPUT_FACTORY.createXMLStreamWriter(stringWriter)));

    marshaller.marshal(event, xmlWriter);
    xmlWriter.flush();

    return stringWriter.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Writes the XML footer (closing tags).
   *
   * @return byte array containing the closing XML tags
   */
  public byte[] getXmlFooter() {
    // Write closing tags as raw XML string since we can't share XMLStreamWriter state
    String footer = "\n</EventList>\n</EPCISBody>\n</epcis:EPCISDocument>";
    return footer.getBytes(StandardCharsets.UTF_8);
  }
}
