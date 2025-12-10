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
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.reactive.util.ReactiveSource;
import io.openepcis.converter.util.IndentingXMLStreamWriter;
import io.openepcis.converter.util.NonEPCISNamespaceXMLStreamWriter;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.util.DefaultJsonSchemaNamespaceURIResolver;
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
  private final DefaultJsonSchemaNamespaceURIResolver namespaceResolver;
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
    this.namespaceResolver = DefaultJsonSchemaNamespaceURIResolver.getContext();
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
    return convert(ReactiveSource.fromPublisher(source));
  }

  /**
   * Converts JSON-LD input to XML byte stream.
   *
   * @param source the conversion source
   * @return Multi emitting XML byte chunks
   */
  public Multi<byte[]> convert(ReactiveSource source) {
    try {
      ObjectNodePublisher<ObjectNode> publisher = createPublisher(source);

      return Multi.createFrom().publisher(publisher)
          .onItem().transformToMultiAndConcatenate(this::processNode)
          .onCompletion().continueWith(() -> Collections.singletonList(getXmlFooter()));

    } catch (IOException e) {
      return Multi.createFrom().failure(
          new FormatConverterException("Failed to initialize JSON parser", e));
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
    return convertToEvents(ReactiveSource.fromPublisher(source));
  }

  /**
   * Converts JSON-LD input to EPCISEvent stream.
   *
   * @param source the conversion source
   * @return Multi emitting EPCISEvent objects
   */
  public Multi<EPCISEvent> convertToEvents(ReactiveSource source) {
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

  private ObjectNodePublisher<ObjectNode> createPublisher(ReactiveSource source)
      throws IOException {
    if (source.hasRetrySupport()) {
      return new ObjectNodePublisher<>(source.toPublisher(), source.retrySource());
    }
    return new ObjectNodePublisher<>(source.toPublisher());
  }

  private boolean isHeaderNode(ObjectNode node) {
    // Header node has @context or type=EPCISDocument/EPCISQueryDocument
    return node.has("@context") ||
        (node.has("type") &&
            (node.get("type").asText().contains("EPCISDocument") ||
                node.get("type").asText().contains("EPCISQueryDocument")));
  }

  private Multi<byte[]> processNode(ObjectNode node) {
    if (isHeaderNode(node)) {
      return processHeader(node);
    } else {
      return processEvent(node);
    }
  }

  private Multi<byte[]> processHeader(ObjectNode headerNode) {
    return Multi.createFrom().emitter(emitter -> {
      try {
        // Extract namespaces from @context
        extractNamespaces(headerNode);

        // Build XML header - don't skip EPCIS namespace in header (need full namespace declarations)
        ByteArrayOutputStream baos = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
        XMLStreamWriter writer = createXmlWriter(baos, false);

        // Write XML declaration and root elements
        writeXmlHeader(writer, headerNode);

        writer.flush();
        byte[] headerBytes = baos.toByteArray();
        if (headerBytes.length > 0) {
          emitter.emit(headerBytes);
        }

        emitter.complete();
      } catch (Exception e) {
        emitter.fail(new FormatConverterException("Failed to write XML header", e));
      }
    });
  }

  private Multi<byte[]> processEvent(ObjectNode eventNode) {
    return Multi.createFrom().emitter(emitter -> {
      try {
        EPCISEvent event = nodeToEvent(eventNode);
        if (event == null) {
          emitter.complete();
          return;
        }

        // Apply event mapper if configured
        Object mappedEvent = eventMapper
            .map(mapper -> mapper.apply(event, Collections.emptyList()))
            .orElse(event);

        if (!(mappedEvent instanceof EPCISEvent)) {
          emitter.complete();
          return;
        }

        // Marshal event to XML
        byte[] xmlBytes = marshalEvent((EPCISEvent) mappedEvent);
        if (xmlBytes.length > 0) {
          emitter.emit(xmlBytes);
        }

        emitter.complete();
      } catch (Exception e) {
        emitter.fail(new FormatConverterException("Failed to marshal event to XML", e));
      }
    });
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

  private void extractNamespaces(ObjectNode headerNode) {
    namespaceResolver.resetAllNamespaces();

    JsonNode contextNode = headerNode.get("@context");
    if (contextNode == null) {
      return;
    }

    if (contextNode.isArray()) {
      for (JsonNode item : contextNode) {
        if (item.isObject()) {
          Iterator<Map.Entry<String, JsonNode>> fields = item.fields();
          while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getValue().isTextual()) {
              namespaceResolver.populateDocumentNamespaces(entry.getValue().asText(), entry.getKey());
            }
          }
        }
      }
    } else if (contextNode.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = contextNode.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        if (entry.getValue().isTextual()) {
          namespaceResolver.populateDocumentNamespaces(entry.getValue().asText(), entry.getKey());
        }
      }
    }
  }

  private XMLStreamWriter createXmlWriter(ByteArrayOutputStream baos, boolean skipEpcisNamespace)
      throws XMLStreamException {
    XMLStreamWriter writer = new IndentingXMLStreamWriter(
        XML_OUTPUT_FACTORY.createXMLStreamWriter(baos, StandardCharsets.UTF_8.name()));
    // Only wrap with NonEPCISNamespaceXMLStreamWriter for event fragments, not for header
    return skipEpcisNamespace ? new NonEPCISNamespaceXMLStreamWriter(writer) : writer;
  }

  private void writeXmlHeader(XMLStreamWriter writer, ObjectNode headerNode)
      throws XMLStreamException {
    writer.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");

    // Write EPCISDocument root element with namespaces
    writer.writeStartElement("epcis", "EPCISDocument",
        "urn:epcglobal:epcis:xsd:2");

    // Write standard namespaces
    writer.writeNamespace("epcis", "urn:epcglobal:epcis:xsd:2");
    writer.writeNamespace("cbvmda", "urn:epcglobal:cbv:mda");
    writer.writeNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");

    // Write custom namespaces from context
    Map<String, String> namespaces = namespaceResolver.getAllNamespaces();
    for (Map.Entry<String, String> ns : namespaces.entrySet()) {
      String prefix = ns.getKey();
      // Skip invalid prefixes: must be valid XML NCName (no colons, slashes, etc.)
      // and not start with "xmlns" or be a URL
      if (prefix != null && !prefix.isEmpty()
          && !prefix.startsWith("xmlns")
          && !prefix.contains(":")
          && !prefix.contains("/")
          && !prefix.startsWith("http")) {
        writer.writeNamespace(prefix, ns.getValue());
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

  private byte[] marshalEvent(EPCISEvent event) throws JAXBException, XMLStreamException {
    Marshaller marshaller = jaxbContext.createMarshaller();
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
    marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
    marshaller.setProperty(
        MarshallerProperties.NAMESPACE_PREFIX_MAPPER,
        namespaceResolver.getAllNamespaces());

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
