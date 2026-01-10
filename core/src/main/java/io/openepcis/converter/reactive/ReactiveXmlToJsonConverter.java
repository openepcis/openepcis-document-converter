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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openepcis.constants.EPCIS;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.util.ConversionNamespaceContext;
import io.openepcis.model.epcis.util.EPCISNamespacePrefixMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.eclipse.persistence.jaxb.JAXBContextProperties;

/**
 * Reactive XML to JSON-LD converter.
 *
 * <p><strong>Important Threading Note:</strong> StAX XML parsing is inherently blocking.
 * This converter buffers the entire XML document into memory before parsing begins.
 * When using in Quarkus or other async runtimes, configure a blocking executor via
 * {@link ReactiveVersionTransformer.Builder#blockingExecutor(java.util.concurrent.Executor)}
 * to avoid EventLoop starvation.
 *
 * <p><strong>Memory Usage:</strong> The entire XML document is buffered in memory before
 * conversion. For documents larger than 100MB, consider alternative approaches or ensure
 * sufficient heap space.
 *
 * <p><strong>Architecture:</strong>
 * <pre>
 * Flow.Publisher&lt;ByteBuffer&gt; (XML input)
 *         |
 *         v
 * ByteArrayOutputStream (full document buffering)
 *         |
 *         v
 * StAX XMLStreamReader (blocking)
 *         |
 *         v
 * JAXB Unmarshalling (XML -&gt; EPCISEvent)
 *         |
 *         v
 * Jackson Serialization (EPCISEvent -&gt; JSON bytes)
 *         |
 *         v
 * Multi&lt;byte[]&gt; (JSON output)
 * </pre>
 *
 * <p><strong>Thread safety:</strong> Instances are NOT thread-safe. Create one instance
 * per conversion operation.
 */
public class ReactiveXmlToJsonConverter {

  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();
  private static final int DEFAULT_BUFFER_SIZE = 8192;

  private final JAXBContext jaxbContext;
  private final ObjectMapper objectMapper;
  private final Optional<BiFunction<Object, List<Object>, Object>> eventMapper;

  static {
    // Configure XMLInputFactory for security
    XML_INPUT_FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    XML_INPUT_FACTORY.setProperty(XMLInputFactory.SUPPORT_DTD, false);
  }

  /**
   * Creates a new converter with default JAXB context.
   *
   * @throws JAXBException if JAXB context creation fails
   */
  public ReactiveXmlToJsonConverter() throws JAXBException {
    this(createDefaultJAXBContext(), Optional.empty());
  }

  /**
   * Creates a new converter with specified JAXB context.
   *
   * @param jaxbContext the JAXB context for unmarshalling
   */
  public ReactiveXmlToJsonConverter(JAXBContext jaxbContext) {
    this(jaxbContext, Optional.empty());
  }

  /**
   * Creates a new converter with specified JAXB context and event mapper.
   *
   * @param jaxbContext the JAXB context for unmarshalling
   * @param eventMapper optional function to transform events after unmarshalling
   */
  public ReactiveXmlToJsonConverter(
      JAXBContext jaxbContext,
      Optional<BiFunction<Object, List<Object>, Object>> eventMapper) {
    this.jaxbContext = Objects.requireNonNull(jaxbContext, "JAXBContext cannot be null");
    this.eventMapper = eventMapper;
    this.objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(SerializationFeature.INDENT_OUTPUT, true)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
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
   * Converts XML input to JSON-LD byte stream.
   *
   * @param source the reactive XML input source
   * @return Multi emitting JSON byte chunks
   */
  public Multi<byte[]> convert(Flow.Publisher<ByteBuffer> source) {
    return convert(ReactiveConversionSource.fromPublisher(source));
  }

  /**
   * Converts XML input to JSON-LD byte stream.
   *
   * @param source the conversion source
   * @return Multi emitting JSON byte chunks
   */
  public Multi<byte[]> convert(ReactiveConversionSource source) {
    // Create conversion-scoped namespace context - captured by lambdas below
    // This replaces the ThreadLocal singleton to avoid thread-switching issues
    final ConversionNamespaceContext nsContext = new ConversionNamespaceContext();

    // Collect all bytes first (StAX needs complete document), then convert
    return source.toMulti()
        .onItem().transform(buffer -> {
          byte[] bytes = new byte[buffer.remaining()];
          buffer.get(bytes);
          return bytes;
        })
        .collect().in(ByteArrayOutputStream::new, (baos, bytes) -> {
          try {
            baos.write(bytes);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        })
        .onItem().transformToMulti(baos ->
            Multi.createFrom().emitter(emitter -> convertFromBytes(baos.toByteArray(), emitter, nsContext)));
  }

  /**
   * Converts XML input to EPCISEvent stream.
   *
   * <p>This provides event-level backpressure - each event is only parsed and
   * converted when downstream requests it.
   *
   * @param source the reactive XML input source
   * @return Multi emitting EPCISEvent objects
   */
  public Multi<EPCISEvent> convertToEvents(Flow.Publisher<ByteBuffer> source) {
    return convertToEvents(ReactiveConversionSource.fromPublisher(source));
  }

  /**
   * Converts XML input to EPCISEvent stream.
   *
   * @param source the conversion source
   * @return Multi emitting EPCISEvent objects
   */
  public Multi<EPCISEvent> convertToEvents(ReactiveConversionSource source) {
    // Create conversion-scoped namespace context - captured by lambdas below
    final ConversionNamespaceContext nsContext = new ConversionNamespaceContext();

    // Collect all bytes first (StAX needs complete document), then parse events
    return source.toMulti()
        .onItem().transform(buffer -> {
          byte[] bytes = new byte[buffer.remaining()];
          buffer.get(bytes);
          return bytes;
        })
        .collect().in(ByteArrayOutputStream::new, (baos, bytes) -> {
          try {
            baos.write(bytes);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        })
        .onItem().transformToMulti(baos ->
            Multi.createFrom().emitter(emitter -> parseEvents(baos.toByteArray(), emitter, nsContext)));
  }

  /**
   * Creates a new converter with event mapper applied.
   *
   * @param mapper function to transform events
   * @return new converter instance with mapper
   */
  public ReactiveXmlToJsonConverter mapWith(BiFunction<Object, List<Object>, Object> mapper) {
    return new ReactiveXmlToJsonConverter(this.jaxbContext, Optional.ofNullable(mapper));
  }

  // ==================== Internal Methods ====================

  private void convertFromBytes(byte[] xmlBytes, MultiEmitter<? super byte[]> emitter,
                                ConversionNamespaceContext nsContext) {
    XMLStreamReader reader = null;
    try (InputStream is = new ByteArrayInputStream(xmlBytes)) {
      reader = XML_INPUT_FACTORY.createXMLStreamReader(is);
      Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

      Map<String, String> contextAttributes = new HashMap<>();
      AtomicInteger sequenceInEventList = new AtomicInteger(0);
      AtomicBoolean headerEmitted = new AtomicBoolean(false);

      // Process XML
      reader.next();

      while (reader.hasNext()) {
        if (reader.isStartElement()) {
          String name = reader.getLocalName();

          // Check for document root
          if (name.toLowerCase().contains(EPCIS.DOCUMENT.toLowerCase())) {
            prepareNamespaces(reader, nsContext);
            prepareContextAttributes(contextAttributes, reader);

            // Emit JSON header
            if (!headerEmitted.getAndSet(true)) {
              byte[] header = buildJsonHeader(contextAttributes, nsContext);
              emitter.emit(header);
            }
          }

          // Check for event types
          if (EPCIS.EPCIS_EVENT_TYPES.contains(name)) {
            // Capture event-level namespaces before unmarshalling (O(1) - no blocking)
            prepareEventNamespaces(reader, nsContext);
            Object event = unmarshallEvent(reader, unmarshaller);

            if (event != null) {
              // Track if this is the first event (sequence starts at 0, increments in applyEventMapper)
              boolean isFirstEvent = sequenceInEventList.get() == 0;

              // Apply mapper if configured
              event = applyEventMapper(sequenceInEventList, event, nsContext);

              // Convert to JSON (with event-level @context if namespaces were discovered)
              byte[] jsonBytes = serializeEvent(event, isFirstEvent, nsContext);
              emitter.emit(jsonBytes);
            }
            continue;
          }
        } else if (reader.isEndElement()) {
          String name = reader.getLocalName();
          if (name.equalsIgnoreCase(EPCIS.EPCIS_DOCUMENT)) {
            // Emit footer
            emitter.emit(buildJsonFooter());
            break;
          }
        }

        reader.next();
      }

      emitter.complete();

    } catch (Exception e) {
      emitter.fail(new FormatConverterException("XML to JSON conversion failed", e));
    } finally {
      // Always close the XMLStreamReader
      if (reader != null) {
        try {
          reader.close();
        } catch (XMLStreamException ignored) {
          // Best effort cleanup
        }
      }
    }
  }

  private void parseEvents(byte[] xmlBytes, MultiEmitter<? super EPCISEvent> emitter,
                           ConversionNamespaceContext nsContext) {
    XMLStreamReader reader = null;
    try (InputStream is = new ByteArrayInputStream(xmlBytes)) {
      reader = XML_INPUT_FACTORY.createXMLStreamReader(is);
      Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

      AtomicInteger sequenceInEventList = new AtomicInteger(0);

      reader.next();

      while (reader.hasNext()) {
        if (reader.isStartElement()) {
          String name = reader.getLocalName();

          if (name.toLowerCase().contains(EPCIS.DOCUMENT.toLowerCase())) {
            prepareNamespaces(reader, nsContext);
          }

          if (EPCIS.EPCIS_EVENT_TYPES.contains(name)) {
            // Capture event-level namespaces before unmarshalling (O(1) - no blocking)
            prepareEventNamespaces(reader, nsContext);
            Object event = unmarshallEvent(reader, unmarshaller);

            if (event != null) {
              event = applyEventMapper(sequenceInEventList, event, nsContext);

              if (event instanceof EPCISEvent) {
                emitter.emit((EPCISEvent) event);
              }
            }
            continue;
          }
        } else if (reader.isEndElement()) {
          String name = reader.getLocalName();
          if (name.equalsIgnoreCase(EPCIS.EPCIS_DOCUMENT)) {
            break;
          }
        }

        reader.next();
      }

      emitter.complete();

    } catch (Exception e) {
      emitter.fail(new FormatConverterException("XML event parsing failed", e));
    } finally {
      // Always close the XMLStreamReader
      if (reader != null) {
        try {
          reader.close();
        } catch (XMLStreamException ignored) {
          // Best effort cleanup
        }
      }
    }
  }

  private Object unmarshallEvent(XMLStreamReader reader, Unmarshaller unmarshaller) {
    try {
      return unmarshaller.unmarshal(reader);
    } catch (JAXBException e) {
      throw new FormatConverterException("Failed to unmarshal event", e);
    }
  }

  private Object applyEventMapper(AtomicInteger sequence, Object event, ConversionNamespaceContext nsContext) {
    if (event instanceof EPCISEvent epcisEvent) {
      epcisEvent.getOpenEPCISExtension().setSequenceInEPCISDoc(sequence.incrementAndGet());
    }

    // Create swapped map for mapper (prefix -> URI format)
    Map<String, String> swappedMap = nsContext.getAllNamespaces().entrySet().stream()
        .collect(java.util.stream.Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    return eventMapper
        .map(mapper -> mapper.apply(event, List.of(swappedMap)))
        .orElse(event);
  }

  /**
   * Captures document-level namespaces from XMLStreamReader.
   * Called when document root element is encountered.
   * These namespaces go into the document-level @context.
   */
  private void prepareNamespaces(XMLStreamReader reader, ConversionNamespaceContext nsContext) {
    int nsCount = reader.getNamespaceCount();
    for (int i = 0; i < nsCount; i++) {
      String prefix = reader.getNamespacePrefix(i);
      String uri = reader.getNamespaceURI(i);
      if (prefix != null && !prefix.isEmpty() && uri != null) {
        nsContext.populateDocumentNamespaces(uri, prefix);
      }
    }
  }

  /**
   * Captures event-level namespaces from XMLStreamReader.
   * Called before unmarshalling each event element.
   * These namespaces go into the event-level @context and are reset after each event.
   * This is O(1) access to already-parsed namespace data - no blocking.
   */
  private void prepareEventNamespaces(XMLStreamReader reader, ConversionNamespaceContext nsContext) {
    int nsCount = reader.getNamespaceCount();
    for (int i = 0; i < nsCount; i++) {
      String prefix = reader.getNamespacePrefix(i);
      String uri = reader.getNamespaceURI(i);
      // Only capture non-standard namespaces as event-level
      if (prefix != null && !prefix.isEmpty() && uri != null && !isStandardNamespace(prefix)) {
        nsContext.populateEventNamespaces(uri, prefix);
      }
    }
  }

  private void prepareContextAttributes(Map<String, String> attributes, XMLStreamReader reader) {
    int attrCount = reader.getAttributeCount();
    for (int i = 0; i < attrCount; i++) {
      String name = reader.getAttributeLocalName(i);
      String value = reader.getAttributeValue(i);
      if (name != null && value != null) {
        attributes.put(name, value);
      }
    }
  }

  private byte[] buildJsonHeader(Map<String, String> contextAttributes,
                                  ConversionNamespaceContext nsContext) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
    JsonGenerator generator = objectMapper.getFactory().createGenerator(baos);

    generator.writeStartObject();

    // Write @context array
    generator.writeArrayFieldStart("@context");
    generator.writeString("https://ref.gs1.org/standards/epcis/epcis-context.jsonld");

    // Add custom namespaces from context (getAllNamespaces returns URI->prefix)
    // But for JSON-LD @context, we need prefix->URI format
    Map<String, String> namespaces = nsContext.getNamespacesForXml(); // This returns prefix->URI
    if (!namespaces.isEmpty()) {
      generator.writeStartObject();
      for (Map.Entry<String, String> ns : namespaces.entrySet()) {
        String prefix = ns.getKey();
        String uri = ns.getValue();
        if (!isStandardNamespace(prefix)) {
          generator.writeStringField(prefix, uri);
        }
      }
      generator.writeEndObject();
    }
    generator.writeEndArray();

    // Write type
    generator.writeStringField("type", "EPCISDocument");

    // Write schemaVersion
    String schemaVersion = contextAttributes.getOrDefault("schemaVersion", "2.0");
    generator.writeStringField("schemaVersion", schemaVersion);

    // Write creationDate if present
    if (contextAttributes.containsKey("creationDate")) {
      generator.writeStringField("creationDate", contextAttributes.get("creationDate"));
    }

    // Start epcisBody and eventList
    generator.writeObjectFieldStart("epcisBody");
    generator.writeArrayFieldStart("eventList");

    generator.flush();
    return baos.toByteArray();
  }

  private byte[] serializeEvent(Object event, boolean isFirstEvent,
                                 ConversionNamespaceContext nsContext) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE);

    // Write comma separator for events after the first
    if (!isFirstEvent) {
      baos.write(',');
    }
    baos.write('\n');

    // Check for event-level namespaces discovered during unmarshalling
    Map<String, String> eventNamespaces = nsContext != null
        ? nsContext.getEventNamespaces()
        : Map.of();

    if (!eventNamespaces.isEmpty()) {
      // Inject event-level @context - serialize event to string first
      String eventJson = objectMapper.writeValueAsString(event);

      // Build @context array for event-level namespaces
      JsonGenerator generator = objectMapper.getFactory().createGenerator(baos);
      generator.writeStartObject();

      // Write @context array with event namespaces
      generator.writeArrayFieldStart("@context");
      for (Map.Entry<String, String> ns : eventNamespaces.entrySet()) {
        generator.writeStartObject();
        generator.writeStringField(ns.getValue(), ns.getKey()); // prefix: uri
        generator.writeEndObject();
      }
      // Add default EPCIS context
      generator.writeString(EPCISVersion.getDefaultJSONContext());
      generator.writeEndArray();

      // Write the rest of the event fields (strip outer braces from serialized event)
      generator.writeRaw("," + eventJson.substring(1, eventJson.length() - 1));
      generator.writeEndObject();
      generator.flush();

      // Reset event namespaces after processing
      nsContext.resetEventNamespaces();
    } else {
      // No event-level namespaces, serialize normally
      objectMapper.writeValue(baos, event);
    }

    return baos.toByteArray();
  }

  private byte[] buildJsonFooter() {
    // Return closing brackets as raw JSON string
    // Close: eventList array, epcisBody object, root object
    return "\n]\n}\n}".getBytes(StandardCharsets.UTF_8);
  }

  private boolean isStandardNamespace(String prefix) {
    return EPCIS.EPCIS_DEFAULT_NAMESPACES.containsKey(prefix) ||
        EPCIS.XSI.equals(prefix) ||
        "xmlns".equals(prefix);
  }
}
