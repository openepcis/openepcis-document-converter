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

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.Conversion;
import io.openepcis.converter.common.GS1FormatSupport;
import io.openepcis.reactive.util.ByteBufferChunker;
import io.openepcis.reactive.util.NettyBufferSupport;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.format.CBVFormat;
import io.openepcis.model.epcis.format.EPCFormat;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.model.epcis.util.EPCISNamespacePrefixMapper;
import io.openepcis.constants.EPCIS;
import io.openepcis.model.epcis.modifier.CustomExtensionAdapter;
import io.openepcis.model.epcis.util.ConversionNamespaceContext;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.io.*;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.stream.*;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.util.EventReaderDelegate;
import io.openepcis.converter.util.IndentingXMLStreamWriter;
import io.openepcis.converter.util.NonEPCISNamespaceXMLStreamWriter;
import org.eclipse.persistence.jaxb.JAXBContextProperties;
import org.eclipse.persistence.jaxb.MarshallerProperties;

/**
 * Reactive document converter with full backpressure support.
 *
 * <p>This is the main entry point for reactive EPCIS document conversion. It orchestrates
 * the conversion pipeline based on input/output formats and versions.
 *
 * <p><strong>Important Threading Note:</strong> Some conversion operations (XML parsing via StAX,
 * XSLT transformations) are inherently blocking. When using this converter in Quarkus or other
 * async runtimes, configure a blocking executor via {@link Builder#blockingExecutor(Executor)}
 * to avoid EventLoop starvation:
 * <pre>{@code
 * ReactiveVersionTransformer transformer = ReactiveVersionTransformer.builder()
 *     .blockingExecutor(Infrastructure.getDefaultWorkerPool())
 *     .build();
 * }</pre>
 *
 * <p><strong>Memory Usage:</strong> XML documents are fully buffered before conversion due to
 * StAX parsing requirements. For documents larger than 100MB, ensure sufficient heap space.
 *
 * <p><strong>Supported conversions:</strong>
 * <ul>
 *   <li>XML 2.0 ↔ JSON-LD 2.0</li>
 *   <li>XML 1.2 → XML 2.0 → JSON-LD 2.0</li>
 *   <li>XML 1.1 → XML 2.0 → JSON-LD 2.0</li>
 *   <li>JSON-LD 2.0 → XML 2.0 → XML 1.2</li>
 *   <li>XML 1.2 ↔ XML 2.0</li>
 *   <li>XML 1.1 → XML 2.0</li>
 *   <li>JSON-LD 2.0 → JSON-LD 2.0 (pass-through/normalization)</li>
 * </ul>
 *
 * <p><strong>Output types:</strong>
 * <ul>
 *   <li>{@code Multi<byte[]>} - Document-level streaming with 8KB chunks</li>
 *   <li>{@code Multi<EPCISEvent>} - Event-level streaming with backpressure</li>
 * </ul>
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * ReactiveVersionTransformer transformer = ReactiveVersionTransformer.builder().build();
 *
 * // From Flow.Publisher (HTTP body, message queue)
 * Flow.Publisher<ByteBuffer> source = ...;
 * Multi<byte[]> result = transformer.convert(source,
 *     Conversion.builder()
 *         .fromMediaType(EPCISFormat.XML)
 *         .toMediaType(EPCISFormat.JSON_LD)
 *         .toVersion(EPCISVersion.VERSION_2_0_0)
 *         .build());
 *
 * // Functional style conversion specification
 * Multi<byte[]> result = transformer.convert(source, c -> c
 *     .fromMediaType(EPCISFormat.XML)
 *     .toMediaType(EPCISFormat.JSON_LD)
 *     .toVersion(EPCISVersion.VERSION_2_0_0));
 *
 * // From InputStream (backward compatibility)
 * try (InputStream is = new FileInputStream("document.xml")) {
 *     Multi<byte[]> result = transformer.convert(is, conversion);
 * }
 *
 * // Event-level streaming
 * Multi<EPCISEvent> events = transformer.convertToEvents(source, conversion);
 * }</pre>
 *
 * <p><strong>Thread safety:</strong> Instances are thread-safe for configuration but
 * individual conversion operations should not be shared across threads.
 *
 * @see ReactiveConversionSource
 * @see Conversion
 */
public class ReactiveVersionTransformer {

  private static final Logger LOG = System.getLogger(ReactiveVersionTransformer.class.getName());

  private static final int DEFAULT_BUFFER_SIZE = 8192;

  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();
  private static final XMLOutputFactory XML_OUTPUT_FACTORY = XMLOutputFactory.newInstance();

  static {
    // Configure XMLInputFactory for security
    XML_INPUT_FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    XML_INPUT_FACTORY.setProperty(XMLInputFactory.SUPPORT_DTD, false);
  }

  /** Flag to ensure we only log the blocking executor warning once per instance */
  private final AtomicBoolean blockingExecutorWarningLogged = new AtomicBoolean(false);

  /**
   * Supported conversions hint for error messages.
   */
  private static final String SUPPORTED_CONVERSIONS_HINT =
      "Supported: XML 2.0 <-> JSON-LD 2.0, XML 1.2/1.1 -> JSON-LD 2.0, " +
      "JSON-LD 2.0 -> XML 2.0/1.2, XML 1.2/1.1 <-> XML 2.0, JSON-LD 2.0 -> JSON-LD 2.0";

  /**
   * Specific suggestions for unsupported conversion paths.
   * All suggestions include "Supported" for test compatibility and end with the hint.
   */
  private static final Map<String, String> CONVERSION_SUGGESTIONS = Map.of(
      "JSON_LD/2.0.0->XML/1.1", "XML 1.1 output not supported. Use XML 1.2 or XML 2.0 instead. " + SUPPORTED_CONVERSIONS_HINT,
      "XML/1.1->JSON_LD/2.0.0", "Conversion is supported. Ensure fromVersion is set to VERSION_1_1_0. " + SUPPORTED_CONVERSIONS_HINT,
      "XML/2.0.0->XML/1.1", "XML 1.1 output not supported. Use XML 1.2 instead. " + SUPPORTED_CONVERSIONS_HINT,
      "XML/1.1->XML/1.2", "Convert XML 1.1 to XML 2.0 first, then to XML 1.2 if needed. " + SUPPORTED_CONVERSIONS_HINT
  );

  private final JAXBContext jaxbContext;
  private final ReactiveXmlToJsonConverter xmlToJsonConverter;
  private final ReactiveJsonToXmlConverter jsonToXmlConverter;
  private final ReactiveXmlVersionTransformer xmlVersionTransformer;
  private final Optional<BiFunction<Object, List<Object>, Object>> eventMapper;
  private final Optional<Executor> blockingExecutor;
  private final int bufferSize;

  private ReactiveVersionTransformer(Builder builder) throws JAXBException {
    this.jaxbContext = builder.jaxbContext != null
        ? builder.jaxbContext
        : createDefaultJAXBContext();
    this.bufferSize = builder.bufferSize;
    this.eventMapper = Optional.ofNullable(builder.eventMapper);
    this.blockingExecutor = Optional.ofNullable(builder.blockingExecutor);

    this.xmlToJsonConverter = new ReactiveXmlToJsonConverter(jaxbContext, eventMapper);
    this.jsonToXmlConverter = new ReactiveJsonToXmlConverter(jaxbContext, eventMapper);
    this.xmlVersionTransformer = createXmlVersionTransformer();
  }

  /**
   * Creates a ReactiveXmlVersionTransformer using ServiceLoader discovery.
   * If a factory is found via ServiceLoader (e.g., SAX-based), it is used.
   * Otherwise, falls back to the default XSLT-based transformer.
   *
   * @return a ReactiveXmlVersionTransformer instance
   */
  private static ReactiveXmlVersionTransformer createXmlVersionTransformer() {
    final Optional<ReactiveXmlVersionTransformerFactory> optionalFactory =
        ServiceLoader.load(ReactiveXmlVersionTransformerFactory.class).findFirst();
    if (optionalFactory.isPresent()) {
      final ReactiveXmlVersionTransformer transformer = optionalFactory.get().newReactiveXmlVersionTransformer();
      LOG.log(Level.INFO, "Using XML version transformer: {0} (via ServiceLoader)", transformer.getClass().getName());
      return transformer;
    }
    // Fallback to default XSLT-based transformer
    LOG.log(Level.INFO, "Using default XSLT-based XML version transformer (no ServiceLoader factory found)");
    return new ReactiveXmlVersionTransformer();
  }

  private ReactiveVersionTransformer(
      ReactiveVersionTransformer parent,
      BiFunction<Object, List<Object>, Object> eventMapper) {
    this.jaxbContext = parent.jaxbContext;
    this.bufferSize = parent.bufferSize;
    this.eventMapper = Optional.ofNullable(eventMapper);
    this.blockingExecutor = parent.blockingExecutor;
    this.xmlVersionTransformer = parent.xmlVersionTransformer;

    this.xmlToJsonConverter = new ReactiveXmlToJsonConverter(jaxbContext, this.eventMapper);
    this.jsonToXmlConverter = new ReactiveJsonToXmlConverter(jaxbContext, this.eventMapper);
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
   * Creates a new builder for ReactiveVersionTransformer.
   *
   * @return new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  // ==================== Primary API: Flow.Publisher Input ====================

  /**
   * Converts a document from reactive ByteBuffer source.
   *
   * @param source the reactive input source
   * @param conversion the conversion specification
   * @return Multi emitting converted document in 8KB chunks
   */
  public Multi<byte[]> convert(Flow.Publisher<ByteBuffer> source, Conversion conversion) {
    return convert(ReactiveConversionSource.fromPublisher(source), conversion);
  }

  /**
   * Converts a document using functional conversion specification.
   *
   * @param source the reactive input source
   * @param fn function to build conversion specification
   * @return Multi emitting converted document in 8KB chunks
   */
  public Multi<byte[]> convert(
      Flow.Publisher<ByteBuffer> source,
      Function<Conversion.StartStage, Conversion.BuildStage> fn) {
    return convert(source, fn.apply(Conversion.builder()).build());
  }

  /**
   * Converts a document to event stream from reactive ByteBuffer source.
   *
   * @param source the reactive input source
   * @param conversion the conversion specification
   * @return Multi emitting EPCISEvent objects
   */
  public Multi<EPCISEvent> convertToEvents(
      Flow.Publisher<ByteBuffer> source, Conversion conversion) {
    return convertToEvents(ReactiveConversionSource.fromPublisher(source), conversion);
  }

  // ==================== Multi<ByteBuffer> Input ====================

  /**
   * Converts a document from Mutiny Multi of ByteBuffers.
   *
   * @param source the Mutiny Multi source
   * @param conversion the conversion specification
   * @return Multi emitting converted document in 8KB chunks
   */
  public Multi<byte[]> convert(Multi<ByteBuffer> source, Conversion conversion) {
    return convert(ReactiveConversionSource.fromMulti(source), conversion);
  }

  /**
   * Converts a document using functional conversion specification.
   *
   * @param source the Mutiny Multi source
   * @param fn function to build conversion specification
   * @return Multi emitting converted document in 8KB chunks
   */
  public Multi<byte[]> convert(
      Multi<ByteBuffer> source,
      Function<Conversion.StartStage, Conversion.BuildStage> fn) {
    return convert(source, fn.apply(Conversion.builder()).build());
  }

  /**
   * Converts a document to event stream from Mutiny Multi of ByteBuffers.
   *
   * @param source the Mutiny Multi source
   * @param conversion the conversion specification
   * @return Multi emitting EPCISEvent objects
   */
  public Multi<EPCISEvent> convertToEvents(Multi<ByteBuffer> source, Conversion conversion) {
    return convertToEvents(ReactiveConversionSource.fromMulti(source), conversion);
  }

  /**
   * Converts a document to event stream using functional conversion specification.
   *
   * @param source the Mutiny Multi source
   * @param fn function to build conversion specification
   * @return Multi emitting EPCISEvent objects
   */
  public Multi<EPCISEvent> convertToEvents(
      Multi<ByteBuffer> source,
      Function<Conversion.StartStage, Conversion.BuildStage> fn) {
    return convertToEvents(source, fn.apply(Conversion.builder()).build());
  }

  // ==================== InputStream Input (Backward Compatibility) ====================

  /**
   * Converts a document from InputStream.
   *
   * @param inputStream the input stream
   * @param conversion the conversion specification
   * @return Multi emitting converted document in 8KB chunks
   */
  public Multi<byte[]> convert(InputStream inputStream, Conversion conversion) {
    return convert(ReactiveConversionSource.fromInputStream(inputStream, bufferSize), conversion);
  }

  /**
   * Converts a document using functional conversion specification.
   *
   * @param inputStream the input stream
   * @param fn function to build conversion specification
   * @return Multi emitting converted document in 8KB chunks
   */
  public Multi<byte[]> convert(
      InputStream inputStream,
      Function<Conversion.StartStage, Conversion.BuildStage> fn) {
    return convert(inputStream, fn.apply(Conversion.builder()).build());
  }

  /**
   * Converts a document to event stream from InputStream.
   *
   * @param inputStream the input stream
   * @param conversion the conversion specification
   * @return Multi emitting EPCISEvent objects
   */
  public Multi<EPCISEvent> convertToEvents(InputStream inputStream, Conversion conversion) {
    return convertToEvents(
        ReactiveConversionSource.fromInputStream(inputStream, bufferSize), conversion);
  }

  /**
   * Converts a document to event stream using functional conversion specification.
   *
   * @param inputStream the input stream
   * @param fn function to build conversion specification
   * @return Multi emitting EPCISEvent objects
   */
  public Multi<EPCISEvent> convertToEvents(
      InputStream inputStream,
      Function<Conversion.StartStage, Conversion.BuildStage> fn) {
    return convertToEvents(inputStream, fn.apply(Conversion.builder()).build());
  }

  // ==================== byte[] Input (Convenience) ====================

  /**
   * Converts a document from byte array.
   *
   * @param bytes the input bytes
   * @param conversion the conversion specification
   * @return Multi emitting converted document in 8KB chunks
   */
  public Multi<byte[]> convert(byte[] bytes, Conversion conversion) {
    return convert(ReactiveConversionSource.fromBytes(bytes), conversion);
  }

  /**
   * Converts a document using functional conversion specification.
   *
   * @param bytes the input bytes
   * @param fn function to build conversion specification
   * @return Multi emitting converted document in 8KB chunks
   */
  public Multi<byte[]> convert(
      byte[] bytes,
      Function<Conversion.StartStage, Conversion.BuildStage> fn) {
    return convert(bytes, fn.apply(Conversion.builder()).build());
  }

  /**
   * Converts a document to event stream from byte array.
   *
   * @param bytes the input bytes
   * @param conversion the conversion specification
   * @return Multi emitting EPCISEvent objects
   */
  public Multi<EPCISEvent> convertToEvents(byte[] bytes, Conversion conversion) {
    return convertToEvents(ReactiveConversionSource.fromBytes(bytes), conversion);
  }

  /**
   * Converts a document to event stream using functional conversion specification.
   *
   * @param bytes the input bytes
   * @param fn function to build conversion specification
   * @return Multi emitting EPCISEvent objects
   */
  public Multi<EPCISEvent> convertToEvents(
      byte[] bytes,
      Function<Conversion.StartStage, Conversion.BuildStage> fn) {
    return convertToEvents(bytes, fn.apply(Conversion.builder()).build());
  }

  // ==================== Multi<ByteBuffer> Output Methods ====================

  /**
   * Converts a document and returns Multi of ByteBuffers.
   *
   * @param source the reactive input source
   * @param conversion the conversion specification
   * @return Multi emitting converted document as ByteBuffers
   */
  public Multi<ByteBuffer> convertToByteBuffers(
      Flow.Publisher<ByteBuffer> source, Conversion conversion) {
    return ByteBufferChunker.toByteBuffers(convert(source, conversion));
  }

  /**
   * Converts a document and returns Multi of ByteBuffers.
   *
   * @param source the Mutiny Multi source
   * @param conversion the conversion specification
   * @return Multi emitting converted document as ByteBuffers
   */
  public Multi<ByteBuffer> convertToByteBuffers(
      Multi<ByteBuffer> source, Conversion conversion) {
    return ByteBufferChunker.toByteBuffers(convert(source, conversion));
  }

  /**
   * Converts a document and returns Multi of ByteBuffers.
   *
   * @param inputStream the input stream
   * @param conversion the conversion specification
   * @return Multi emitting converted document as ByteBuffers
   */
  public Multi<ByteBuffer> convertToByteBuffers(
      InputStream inputStream, Conversion conversion) {
    return ByteBufferChunker.toByteBuffers(convert(inputStream, conversion));
  }

  /**
   * Converts a document and returns Multi of ByteBuffers.
   *
   * @param bytes the input bytes
   * @param conversion the conversion specification
   * @return Multi emitting converted document as ByteBuffers
   */
  public Multi<ByteBuffer> convertToByteBuffers(byte[] bytes, Conversion conversion) {
    return ByteBufferChunker.toByteBuffers(convert(bytes, conversion));
  }

  /**
   * Converts a document and returns Multi of ByteBuffers.
   *
   * @param source the conversion source
   * @param conversion the conversion specification
   * @return Multi emitting converted document as ByteBuffers
   */
  public Multi<ByteBuffer> convertToByteBuffers(
      ReactiveConversionSource source, Conversion conversion) {
    return ByteBufferChunker.toByteBuffers(convert(source, conversion));
  }

  // ==================== Netty ByteBuf Input Methods ====================

  /**
   * Converts a document from Netty ByteBuf publisher.
   *
   * <p>ByteBufs are automatically converted to ByteBuffers. The original ByteBufs
   * are released after conversion.
   *
   * @param source the Netty ByteBuf publisher
   * @param conversion the conversion specification
   * @return Multi emitting converted document in 8KB chunks
   * @throws IllegalStateException if Netty is not available
   */
  public Multi<byte[]> convertFromNetty(
      Flow.Publisher<io.netty.buffer.ByteBuf> source, Conversion conversion) {
    return convert(ReactiveConversionSource.fromNettyPublisher(source), conversion);
  }

  /**
   * Converts a document from Mutiny Multi of Netty ByteBufs.
   *
   * @param source the Mutiny Multi of ByteBufs
   * @param conversion the conversion specification
   * @return Multi emitting converted document in 8KB chunks
   * @throws IllegalStateException if Netty is not available
   */
  public Multi<byte[]> convertFromNetty(
      Multi<io.netty.buffer.ByteBuf> source, Conversion conversion) {
    return convert(ReactiveConversionSource.fromNettyMulti(source), conversion);
  }

  /**
   * Converts a document to event stream from Netty ByteBuf publisher.
   *
   * @param source the Netty ByteBuf publisher
   * @param conversion the conversion specification
   * @return Multi emitting EPCISEvent objects
   * @throws IllegalStateException if Netty is not available
   */
  public Multi<EPCISEvent> convertToEventsFromNetty(
      Flow.Publisher<io.netty.buffer.ByteBuf> source, Conversion conversion) {
    return convertToEvents(ReactiveConversionSource.fromNettyPublisher(source), conversion);
  }

  /**
   * Converts a document to event stream from Mutiny Multi of Netty ByteBufs.
   *
   * @param source the Mutiny Multi of ByteBufs
   * @param conversion the conversion specification
   * @return Multi emitting EPCISEvent objects
   * @throws IllegalStateException if Netty is not available
   */
  public Multi<EPCISEvent> convertToEventsFromNetty(
      Multi<io.netty.buffer.ByteBuf> source, Conversion conversion) {
    return convertToEvents(ReactiveConversionSource.fromNettyMulti(source), conversion);
  }

  // ==================== Netty ByteBuf Output Methods ====================

  /**
   * Converts a document and returns Multi of Netty ByteBufs.
   *
   * <p><strong>Important:</strong> Consumers MUST call {@code ByteBuf.release()}
   * on each emitted buffer to prevent memory leaks.
   *
   * @param source the Netty ByteBuf publisher
   * @param conversion the conversion specification
   * @return Multi emitting Netty ByteBufs
   * @throws IllegalStateException if Netty is not available
   */
  public Multi<io.netty.buffer.ByteBuf> convertToNettyBuffers(
      Flow.Publisher<io.netty.buffer.ByteBuf> source, Conversion conversion) {
    return NettyBufferSupport.toNettyBuffers(convertFromNetty(source, conversion));
  }

  /**
   * Converts a document and returns Multi of Netty ByteBufs.
   *
   * <p><strong>Important:</strong> Consumers MUST call {@code ByteBuf.release()}
   * on each emitted buffer to prevent memory leaks.
   *
   * @param source the Mutiny Multi of ByteBufs
   * @param conversion the conversion specification
   * @return Multi emitting Netty ByteBufs
   * @throws IllegalStateException if Netty is not available
   */
  public Multi<io.netty.buffer.ByteBuf> convertToNettyBuffers(
      Multi<io.netty.buffer.ByteBuf> source, Conversion conversion) {
    return NettyBufferSupport.toNettyBuffers(convertFromNetty(source, conversion));
  }

  /**
   * Converts a document and returns Multi of Netty ByteBufs.
   *
   * <p><strong>Important:</strong> Consumers MUST call {@code ByteBuf.release()}
   * on each emitted buffer to prevent memory leaks.
   *
   * @param bytes the input bytes
   * @param conversion the conversion specification
   * @return Multi emitting Netty ByteBufs
   * @throws IllegalStateException if Netty is not available
   */
  public Multi<io.netty.buffer.ByteBuf> convertToNettyBuffers(byte[] bytes, Conversion conversion) {
    return NettyBufferSupport.toNettyBuffers(convert(bytes, conversion));
  }

  /**
   * Converts a document and returns Multi of Netty ByteBufs.
   *
   * <p><strong>Important:</strong> Consumers MUST call {@code ByteBuf.release()}
   * on each emitted buffer to prevent memory leaks.
   *
   * @param source the conversion source
   * @param conversion the conversion specification
   * @return Multi emitting Netty ByteBufs
   * @throws IllegalStateException if Netty is not available
   */
  public Multi<io.netty.buffer.ByteBuf> convertToNettyBuffers(
      ReactiveConversionSource source, Conversion conversion) {
    return NettyBufferSupport.toNettyBuffers(convert(source, conversion));
  }

  // ==================== Core Conversion Logic ====================

  /**
   * Converts a document from ReactiveConversionSource.
   *
   * @param source the conversion source
   * @param conversion the conversion specification
   * @return Multi emitting converted document in 8KB chunks
   */
  public Multi<byte[]> convert(ReactiveConversionSource source, Conversion conversion) {
    Objects.requireNonNull(source, "Source cannot be null");
    Objects.requireNonNull(conversion, "Conversion cannot be null");

    return routeConversion(source, conversion);
  }

  /**
   * Converts a document to event stream from ReactiveConversionSource.
   *
   * @param source the conversion source
   * @param conversion the conversion specification
   * @return Multi emitting EPCISEvent objects
   */
  public Multi<EPCISEvent> convertToEvents(ReactiveConversionSource source, Conversion conversion) {
    Objects.requireNonNull(source, "Source cannot be null");
    Objects.requireNonNull(conversion, "Conversion cannot be null");

    return routeConversionToEvents(source, conversion);
  }

  // ==================== Event Mapper Support ====================

  /**
   * Creates a new transformer with event mapper applied.
   *
   * @param mapper function to transform events during conversion
   * @return new transformer instance with mapper
   */
  public ReactiveVersionTransformer mapWith(BiFunction<Object, List<Object>, Object> mapper) {
    return new ReactiveVersionTransformer(this, mapper);
  }

  /**
   * Creates a new transformer with typed event mapper applied.
   *
   * <p>This is the recommended way to configure event mapping as it provides type safety.
   *
   * <p><strong>Example:</strong>
   * <pre>{@code
   * EPCISEventMapper mapper = (event, context) -> {
   *     // Transform event...
   *     return event;
   * };
   * ReactiveVersionTransformer mapped = transformer.mapWithTyped(mapper);
   * }</pre>
   *
   * @param mapper typed mapper to transform events during conversion
   * @return new transformer instance with mapper
   * @see EPCISEventMapper
   */
  public ReactiveVersionTransformer mapWithTyped(EPCISEventMapper mapper) {
    return new ReactiveVersionTransformer(this, mapper.toBiFunction());
  }

  // ==================== Conversion Routing ====================

  /**
   * Applies blocking executor to the Multi if one is configured.
   * This ensures blocking operations run on a dedicated thread pool.
   *
   * <p>If no blocking executor is configured, logs a warning once per instance
   * to alert users in async contexts (Quarkus, Vert.x) about potential EventLoop starvation.
   */
  private <T> Multi<T> withBlockingExecutor(Multi<T> multi) {
    if (blockingExecutor.isEmpty() && blockingExecutorWarningLogged.compareAndSet(false, true)) {
      LOG.log(Level.WARNING, "No blocking executor configured. Blocking operations (XSLT, StAX) " +
               "will run on the subscription thread. In async contexts (Quarkus, Vert.x), " +
               "configure a blocking executor via builder to avoid EventLoop starvation: " +
               "ReactiveVersionTransformer.builder().blockingExecutor(Infrastructure.getDefaultWorkerPool()).build()");
    }
    return blockingExecutor
        .map(executor -> multi.runSubscriptionOn(executor))
        .orElse(multi);
  }

  private Multi<byte[]> routeConversion(ReactiveConversionSource source, Conversion conversion) {
    EPCISFormat fromFormat = conversion.fromMediaType();
    EPCISFormat toFormat = conversion.toMediaType();
    EPCISVersion fromVersion = conversion.fromVersion();
    EPCISVersion toVersion = conversion.toVersion();

    // XML -> XML and
    // Always apply format transformation with default preferences based on target version:
    // - XML 2.0 target: Digital Link format (Always_GS1_Digital_Link, Always_Web_URI)
    // - XML 1.2 target: URN format (Always_EPC_URN, Always_URN)
    // User-provided mapper via headers will override the default.
    if (EPCISFormat.XML.equals(fromFormat) && EPCISFormat.XML.equals(toFormat)) {
      return withBlockingExecutor(convertXmlToXmlWithFormatPreferences(source, conversion));
    }

    // JSON-LD -> XML 2.0 - non-blocking JSON parsing, blocking JAXB marshalling
    // Default format: Digital Link (Always_GS1_Digital_Link, Always_Web_URI)
    if (EPCISFormat.JSON_LD.equals(fromFormat) && EPCISFormat.XML.equals(toFormat) && EPCISVersion.VERSION_2_0_0.equals(toVersion)) {
      BiFunction<Object, List<Object>, Object> effectiveMapper = getEffectiveMapper(EPCISVersion.VERSION_2_0_0);
      ReactiveJsonToXmlConverter jsonToXmlWithMapper = new ReactiveJsonToXmlConverter(jaxbContext, Optional.of(effectiveMapper));
      return withBlockingExecutor(jsonToXmlWithMapper.convert(source));
    }

    // JSON-LD -> XML 1.2 (JSON -> XML 2.0 -> XML 1.2) - blocking JAXB + XSLT
    // Default format: URN (Always_EPC_URN, Always_URN) for XML 1.2
    // Chain the converters: pipe XML 2.0 output to XML 1.2 transformer input
    if (EPCISFormat.JSON_LD.equals(fromFormat) && EPCISFormat.XML.equals(toFormat)
        && EPCISVersion.VERSION_1_2_0.equals(toVersion)) {
      BiFunction<Object, List<Object>, Object> effectiveMapper = getEffectiveMapper(EPCISVersion.VERSION_1_2_0);
      ReactiveJsonToXmlConverter jsonToXmlWithMapper = new ReactiveJsonToXmlConverter(jaxbContext, Optional.of(effectiveMapper));
      return withBlockingExecutor(
          chainMultiToMulti(
              jsonToXmlWithMapper.convert(source),
              xml20Bytes -> xmlVersionTransformer.transform(
                  ReactiveConversionSource.fromMulti(xml20Bytes.onItem().transform(ByteBuffer::wrap)),
                  Conversion.builder()
                      .generateGS1CompliantDocument(conversion.generateGS1CompliantDocument().orElse(null))
                      .fromMediaType(null)
                      .fromVersion(EPCISVersion.VERSION_2_0_0)
                      .toVersion(EPCISVersion.VERSION_1_2_0)
                      .build())));
    }

    // XML 2.0 -> JSON-LD 2.0 - blocking StAX parsing
    // Default format: Digital Link (Always_GS1_Digital_Link, Always_Web_URI) for JSON 2.0
    if (EPCISFormat.XML.equals(fromFormat) && EPCISFormat.JSON_LD.equals(toFormat)
        && EPCISVersion.VERSION_2_0_0.equals(fromVersion)) {
      BiFunction<Object, List<Object>, Object> effectiveMapper = getEffectiveMapper(EPCISVersion.VERSION_2_0_0);
      ReactiveXmlToJsonConverter xmlToJsonWithMapper = new ReactiveXmlToJsonConverter(jaxbContext, Optional.of(effectiveMapper));
      return withBlockingExecutor(xmlToJsonWithMapper.convert(source));
    }

    // XML 1.2 -> JSON-LD 2.0 (XML 1.2 -> XML 2.0 -> JSON) - blocking XSLT + StAX
    // Default format: Digital Link (Always_GS1_Digital_Link, Always_Web_URI) for JSON 2.0
    if (EPCISFormat.XML.equals(fromFormat) && EPCISFormat.JSON_LD.equals(toFormat)
        && (EPCISVersion.VERSION_1_2_0.equals(fromVersion) || EPCISVersion.VERSION_1_1_0.equals(fromVersion))) {
      BiFunction<Object, List<Object>, Object> effectiveMapper = getEffectiveMapper(EPCISVersion.VERSION_2_0_0);
      ReactiveXmlToJsonConverter xmlToJsonWithMapper = new ReactiveXmlToJsonConverter(jaxbContext, Optional.of(effectiveMapper));
      return withBlockingExecutor(xmlVersionTransformer.transform(source,
              Conversion.of(null, fromVersion, null, EPCISVersion.VERSION_2_0_0))
          .plug(bytes -> xmlToJsonWithMapper.convert(
              ReactiveConversionSource.fromMulti(
                  bytes.onItem().transform(b -> ByteBuffer.wrap(b))))));
    }

    // JSON-LD -> JSON-LD (format normalization)
    if (EPCISFormat.JSON_LD.equals(fromFormat) && EPCISFormat.JSON_LD.equals(toFormat)) {
      // Pass through with optional event mapping
      return source.toMulti()
          .onItem().transform(buffer -> {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
          });
    }

    return Multi.createFrom().failure(
        new UnsupportedOperationException(buildUnsupportedConversionMessage(
            fromFormat, fromVersion, toFormat, toVersion)));
  }

  /**
   * Builds an error message for unsupported conversions with specific suggestions when available.
   */
  private String buildUnsupportedConversionMessage(
      EPCISFormat fromFormat, EPCISVersion fromVersion,
      EPCISFormat toFormat, EPCISVersion toVersion) {
    String conversionKey = fromFormat + "/" + (fromVersion != null ? fromVersion.getVersion() : "null")
        + "->" + toFormat + "/" + (toVersion != null ? toVersion.getVersion() : "null");

    // Try to find a specific suggestion
    String suggestion = CONVERSION_SUGGESTIONS.entrySet().stream()
        .filter(e -> conversionKey.contains(e.getKey()))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(SUPPORTED_CONVERSIONS_HINT);

    return "Unsupported conversion: " + fromFormat + "/" + fromVersion
        + " -> " + toFormat + "/" + toVersion + ". " + suggestion;
  }

  private Multi<EPCISEvent> routeConversionToEvents(
      ReactiveConversionSource source, Conversion conversion) {
    EPCISFormat fromFormat = conversion.fromMediaType();
    EPCISVersion fromVersion = conversion.fromVersion();

    // JSON-LD input - non-blocking JSON parsing
    if (EPCISFormat.JSON_LD.equals(fromFormat)) {
      return withBlockingExecutor(jsonToXmlConverter.convertToEvents(source));
    }

    // XML 2.0 input - blocking StAX parsing
    if (EPCISFormat.XML.equals(fromFormat) && EPCISVersion.VERSION_2_0_0.equals(fromVersion)) {
      return withBlockingExecutor(xmlToJsonConverter.convertToEvents(source));
    }

    // XML 1.2 input (transform to 2.0 first) - blocking XSLT + StAX
    if (EPCISFormat.XML.equals(fromFormat)
        && (EPCISVersion.VERSION_1_2_0.equals(fromVersion) || EPCISVersion.VERSION_1_1_0.equals(fromVersion))) {
      return withBlockingExecutor(xmlVersionTransformer.transform(source,
              Conversion.of(null, fromVersion, null, EPCISVersion.VERSION_2_0_0))
          .plug(bytes -> xmlToJsonConverter.convertToEvents(
              ReactiveConversionSource.fromMulti(
                  bytes.onItem().transform(b -> ByteBuffer.wrap(b))))));
    }

    return Multi.createFrom().failure(
        new UnsupportedOperationException(
            "Cannot extract events from: " + fromFormat + "/" + fromVersion
                + ". Supported input formats: JSON-LD 2.0, XML 2.0, XML 1.2, XML 1.1"));
  }

  // ==================== XML to XML with Format Preferences ====================

  /**
   * Gets the effective event mapper for XML-to-XML conversion.
   *
   * <p>If a user-provided mapper is present, it is used. Otherwise, a default mapper is
   * created based on the target version:
   * <ul>
   *   <li>XML 2.0 target: Digital Link format (Always_GS1_Digital_Link, Always_Web_URI)</li>
   *   <li>XML 1.2 target: URN format (Always_EPC_URN, Always_URN)</li>
   * </ul>
   *
   * @param toVersion the target EPCIS version
   * @return the effective event mapper
   */
  private BiFunction<Object, List<Object>, Object> getEffectiveMapper(EPCISVersion toVersion) {
    // If user explicitly provided a mapper, use it
    if (eventMapper.isPresent()) {
      return eventMapper.get();
    }

    // Create default mapper based on target version
    FormatPreference defaultPreference;
    if (EPCISVersion.VERSION_1_2_0.equals(toVersion)) {
      // XML 1.2 prefers URN format
      defaultPreference = FormatPreference.getInstance(EPCFormat.Always_EPC_URN, CBVFormat.Always_URN);
    } else {
      // XML 2.0 prefers Digital Link format
      defaultPreference = FormatPreference.getInstance(EPCFormat.Always_GS1_Digital_Link, CBVFormat.Always_Web_URI);
    }

    return GS1FormatSupport.createMapper(defaultPreference);
  }

  /**
   * Converts XML to XML with format preference application.
   *
   * <p>This method handles XML-to-XML conversion with format transformation (URN ↔ Digital Link)
   * entirely within this class, without using external converters.
   *
   * <p>Default format preferences based on target version:
   * <ul>
   *   <li>XML 2.0 target: Digital Link format (Always_GS1_Digital_Link, Always_Web_URI)</li>
   *   <li>XML 1.2 target: URN format (Always_EPC_URN, Always_URN)</li>
   * </ul>
   *
   * @param source the input XML source
   * @param conversion the conversion specification
   * @return Multi emitting transformed XML bytes
   */
  private Multi<byte[]> convertXmlToXmlWithFormatPreferences(
      ReactiveConversionSource source, Conversion conversion) {

    EPCISVersion fromVersion = conversion.fromVersion();
    EPCISVersion toVersion = conversion.toVersion();

    // Get the effective mapper (user-provided or default based on target version)
    BiFunction<Object, List<Object>, Object> effectiveMapper = getEffectiveMapper(toVersion);

    // Map to capture document-level attributes (creationDate, schemaVersion, etc.) during parsing
    final Map<String, String> documentAttributes = new ConcurrentHashMap<>();

    // Step 1: If input is XML 1.2/1.1, transform to XML 2.0 first (required for parsing)
    ReactiveConversionSource xml20Source = source;
    if (fromVersion != null && !EPCISVersion.VERSION_2_0_0.equals(fromVersion)) {
      xml20Source = ReactiveConversionSource.fromMulti(
          xmlVersionTransformer.transform(source,
              Conversion.of(null, fromVersion, null, EPCISVersion.VERSION_2_0_0))
          .onItem().transform(ByteBuffer::wrap));
    }

    // Step 2: Parse XML 2.0 to events (internal method - NO mapper applied during parsing)
    Multi<EPCISEvent> events = parseXmlToEvents(xml20Source, documentAttributes);

    // Step 3: Marshal events back to XML 2.0 with format transformation
    Multi<byte[]> xml20WithFormats = marshalEventsToXml(events, effectiveMapper, documentAttributes);

    // Step 4: If target is XML 1.2, transform XML 2.0 → XML 1.2
    if (EPCISVersion.VERSION_1_2_0.equals(toVersion)) {
      return chainMultiToMulti(
          xml20WithFormats,
          xml20Bytes -> xmlVersionTransformer.transform(
              ReactiveConversionSource.fromMulti(xml20Bytes.onItem().transform(ByteBuffer::wrap)),
              Conversion.builder()
                  .generateGS1CompliantDocument(conversion.generateGS1CompliantDocument().orElse(null))
                  .fromMediaType(null)
                  .fromVersion(EPCISVersion.VERSION_2_0_0)
                  .toVersion(EPCISVersion.VERSION_1_2_0)
                  .build()));
    }

    return xml20WithFormats;
  }

  // ==================== Internal XML Parsing/Marshalling for XML-to-XML ====================

  /**
   * Parses XML 2.0 to EPCISEvent objects using StAX + JAXB.
   * This method is used internally for XML-to-XML conversion.
   * NO event mapper is applied during parsing - just pure parsing.
   *
   * @param source the XML 2.0 source
   * @return Multi emitting EPCISEvent objects
   */
  private Multi<EPCISEvent> parseXmlToEvents(ReactiveConversionSource source,
                                              Map<String, String> documentAttributes) {
    // Create conversion-scoped namespace context
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
            Multi.createFrom().emitter(emitter -> parseEventsInternal(baos.toByteArray(), emitter, nsContext, documentAttributes)));
  }

  /**
   * Internal method to parse events from XML bytes.
   */
  private void parseEventsInternal(byte[] xmlBytes, MultiEmitter<? super EPCISEvent> emitter,
                                    ConversionNamespaceContext nsContext,
                                    Map<String, String> documentAttributes) {
    XMLStreamReader reader = null;
    try (InputStream is = new ByteArrayInputStream(xmlBytes)) {
      reader = XML_INPUT_FACTORY.createXMLStreamReader(is);
      Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
      // Inject namespace context into CustomExtensionAdapter for ILMD inline namespace discovery
      unmarshaller.setAdapter(CustomExtensionAdapter.class, new CustomExtensionAdapter(nsContext));

      AtomicInteger sequenceInEventList = new AtomicInteger(0);

      reader.next();

      while (reader.hasNext()) {
        if (reader.isStartElement()) {
          String name = reader.getLocalName();

          if (name.toLowerCase().contains(EPCIS.DOCUMENT.toLowerCase())) {
            prepareDocumentNamespaces(reader, nsContext);
            // Capture document-level attributes (creationDate, schemaVersion, etc.)
            for (int i = 0; i < reader.getAttributeCount(); i++) {
              documentAttributes.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
            }
          }

          if (EPCIS.EPCIS_EVENT_TYPES.contains(name)) {
            // Create per-event scoped context with document namespaces + this event's namespaces
            ConversionNamespaceContext eventScopedCtx = ConversionNamespaceContext.createEventScoped(nsContext);
            prepareEventNamespaces(reader, eventScopedCtx);
            Object event = unmarshallEventInternal(reader, unmarshaller, eventScopedCtx);

            if (event instanceof EPCISEvent epcisEvent) {
              epcisEvent.getOpenEPCISExtension().setSequenceInEPCISDoc(sequenceInEventList.incrementAndGet());
              epcisEvent.getOpenEPCISExtension().setConversionNamespaceContext(eventScopedCtx);
              emitter.emit(epcisEvent);
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
      if (reader != null) {
        try {
          reader.close();
        } catch (XMLStreamException ignored) {
          // Best effort cleanup
        }
      }
    }
  }

  /**
   * Captures document-level namespaces from XMLStreamReader.
   */
  private void prepareDocumentNamespaces(XMLStreamReader reader, ConversionNamespaceContext nsContext) {
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
   */
  private void prepareEventNamespaces(XMLStreamReader reader, ConversionNamespaceContext nsContext) {
    int nsCount = reader.getNamespaceCount();
    for (int i = 0; i < nsCount; i++) {
      String prefix = reader.getNamespacePrefix(i);
      String uri = reader.getNamespaceURI(i);
      if (prefix != null && !prefix.isEmpty() && uri != null) {
        nsContext.populateEventNamespaces(uri, prefix);
      }
    }
  }

  /**
   * Unmarshals a single event from the XMLStreamReader.
   */
  private Object unmarshallEventInternal(XMLStreamReader reader, Unmarshaller unmarshaller,
                                          ConversionNamespaceContext nsContext) {
    try {
      // Set ThreadLocal context for EPCISEvent.afterUnmarshal() to discover extension namespaces
      ConversionNamespaceContext.setUnmarshalContext(nsContext);
      try {
        return unmarshaller.unmarshal(reader);
      } finally {
        ConversionNamespaceContext.clearUnmarshalContext();
      }
    } catch (JAXBException e) {
      throw new FormatConverterException("Failed to unmarshal event", e);
    }
  }

  /**
   * Marshals EPCISEvent objects to XML 2.0 with format transformation.
   * The format mapper is applied to each event before marshalling.
   *
   * @param events the stream of events to marshal
   * @param mapper the format mapper to apply (URN ↔ Digital Link transformation)
   * @return Multi emitting XML byte chunks
   */
  private Multi<byte[]> marshalEventsToXml(Multi<EPCISEvent> events,
                                            BiFunction<Object, List<Object>, Object> mapper,
                                            Map<String, String> documentAttributes) {

    final ConversionNamespaceContext documentNsContext = new ConversionNamespaceContext();
    final AtomicBoolean headerEmitted = new AtomicBoolean(false);
    final AtomicInteger sequence = new AtomicInteger(0);

    return events
        .onItem().transformToMultiAndConcatenate(event -> {
          try {
            ConversionNamespaceContext eventCtx = null;
            if (event.getOpenEPCISExtension() != null &&
                event.getOpenEPCISExtension().getConversionNamespaceContext() != null) {
              eventCtx = event.getOpenEPCISExtension().getConversionNamespaceContext();
            }

            // Copy only DOCUMENT-level namespaces to documentNsContext (for the header)
            if (eventCtx != null) {
              for (Map.Entry<String, String> ns : eventCtx.getDocumentNamespaces().entrySet()) {
                documentNsContext.populateDocumentNamespaces(ns.getKey(), ns.getValue());
              }
            }

            // Create event-scoped context: document namespaces + this event's event-level namespaces
            ConversionNamespaceContext eventScopedContext = ConversionNamespaceContext.createEventScoped(documentNsContext);
            if (eventCtx != null) {
              for (Map.Entry<String, String> ns : eventCtx.getEventNamespaces().entrySet()) {
                eventScopedContext.populateEventNamespaces(ns.getKey(), ns.getValue());
              }
            }

            // Emit header before first event
            if (!headerEmitted.getAndSet(true)) {
              byte[] headerBytes = createXmlHeaderInternal(documentNsContext, documentAttributes);
              byte[] eventBytes = marshalEventInternal(event, eventScopedContext, sequence, mapper);

              ByteArrayOutputStream combined = new ByteArrayOutputStream();
              combined.write(headerBytes);
              if (eventBytes != null && eventBytes.length > 0) {
                combined.write(eventBytes);
              }
              return Multi.createFrom().item(combined.toByteArray());
            }

            // Marshal subsequent events
            byte[] eventBytes = marshalEventInternal(event, eventScopedContext, sequence, mapper);

            if (eventBytes != null && eventBytes.length > 0) {
              return Multi.createFrom().item(eventBytes);
            }
            return Multi.createFrom().empty();

          } catch (Exception e) {
            return Multi.createFrom().failure(
                new FormatConverterException("Failed to marshal event: " + e.getMessage(), e));
          }
        })
        .onCompletion().continueWith(() -> {
          // Emit footer
          return List.of(createXmlFooterInternal());
        });
  }

  /**
   * Marshals a single EPCISEvent to XML bytes with format transformation.
   */
  private byte[] marshalEventInternal(EPCISEvent event,
                                      ConversionNamespaceContext nsContext,
                                      AtomicInteger sequence,
                                      BiFunction<Object, List<Object>, Object> mapper) throws JAXBException, XMLStreamException {

    event.getOpenEPCISExtension().setSequenceInEPCISDoc(sequence.incrementAndGet());
    event.getOpenEPCISExtension().setConversionNamespaceContext(nsContext);

    // Apply format mapper (EPCISEventES transformation)
    Map<String, String> swappedMap = nsContext.getEventNamespaces().entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    Object mappedEvent = mapper != null ? mapper.apply(event, List.of(swappedMap)) : event;

    if (!(mappedEvent instanceof EPCISEvent)) {
      return null;
    }

    // Create marshaller with namespace mapper
    Marshaller marshaller = jaxbContext.createMarshaller();
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
    marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
    marshaller.setProperty(MarshallerProperties.NAMESPACE_PREFIX_MAPPER, nsContext.getEventNamespaces());
    marshaller.setAdapter(CustomExtensionAdapter.class, new CustomExtensionAdapter(nsContext));

    // Marshal to string - let JAXB write all necessary namespace declarations on each event
    StringWriter singleXmlEvent = new StringWriter();
    XMLStreamWriter xmlStreamWriter = new NonEPCISNamespaceXMLStreamWriter(new IndentingXMLStreamWriter(XML_OUTPUT_FACTORY.createXMLStreamWriter(singleXmlEvent)));
    marshaller.marshal(mappedEvent, xmlStreamWriter);
    xmlStreamWriter.flush();

    // Collect event XML
    ByteArrayOutputStream baos = new ByteArrayOutputStream(2048);
    XMLEventWriter xmlEventWriter = XML_OUTPUT_FACTORY.createXMLEventWriter(baos, StandardCharsets.UTF_8.name());

    try {
      collectEventInternal(xmlEventWriter, singleXmlEvent.toString());
      xmlEventWriter.flush();
      return baos.toByteArray();
    } finally {
      xmlEventWriter.close();
    }
  }

  /**
   * Collects an event by parsing its XML and adding to the XMLEventWriter.
   */
  private void collectEventInternal(XMLEventWriter xmlEventWriter, String eventXml) throws XMLStreamException {
    XMLEventReader xer =
        new EventReaderDelegate(XML_INPUT_FACTORY.createXMLEventReader(new StringReader(eventXml))) {
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
   * Creates the XML header for EPCIS 2.0 document.
   */
  private byte[] createXmlHeaderInternal(ConversionNamespaceContext nsContext,
                                          Map<String, String> documentAttributes)
      throws XMLStreamException {

    ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
    XMLEventWriter xmlEventWriter = XML_OUTPUT_FACTORY.createXMLEventWriter(baos, StandardCharsets.UTF_8.name());
    XMLEventFactory events = XMLEventFactory.newInstance();

    try {
      // Write XML declaration
      xmlEventWriter.add(events.createStartDocument(StandardCharsets.UTF_8.name(), "1.0"));
      xmlEventWriter.add(events.createCharacters("\n"));

      // Start EPCISDocument with namespaces
      xmlEventWriter.add(events.createStartElement(
          "epcis", EPCIS.EPCIS_2_0_XMLNS, "EPCISDocument"));
      xmlEventWriter.add(events.createNamespace("epcis", EPCIS.EPCIS_2_0_XMLNS));
      xmlEventWriter.add(events.createNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance"));

      // Add custom namespaces from context
      for (Map.Entry<String, String> ns : nsContext.getNamespacesForXml().entrySet()) {
        String prefix = ns.getKey();
        String uri = ns.getValue();
        if (!prefix.isEmpty() && !prefix.equals("epcis") && !prefix.equals("xsi")) {
          xmlEventWriter.add(events.createNamespace(prefix, uri));
        }
      }

      // Add schemaVersion and creationDate attributes (preserve original if available)
      xmlEventWriter.add(events.createAttribute("schemaVersion", "2.0"));
      String creationDate = documentAttributes != null ? documentAttributes.get("creationDate") : null;
      xmlEventWriter.add(events.createAttribute("creationDate",
          creationDate != null ? creationDate : Instant.now().toString()));

      // Start EPCISBody
      xmlEventWriter.add(events.createStartElement("", "", "EPCISBody"));

      // Start EventList
      xmlEventWriter.add(events.createStartElement("", "", "EventList"));
      xmlEventWriter.add(events.createCharacters(""));

      xmlEventWriter.flush();
      return baos.toByteArray();

    } finally {
      xmlEventWriter.close();
    }
  }

  /**
   * Creates the XML footer for EPCIS 2.0 document.
   */
  private byte[] createXmlFooterInternal() {
    return "\n</EventList>\n</EPCISBody>\n</epcis:EPCISDocument>".getBytes(StandardCharsets.UTF_8);
  }

  // ==================== Helper Methods ====================

  /**
   * Chains two Multi pipelines using a PipedInputStream/PipedOutputStream pair.
   * The upstream Multi writes to a pipe, and the downstream Multi reads from it.
   * This enables true streaming between converters without buffering everything in memory.
   *
   * <p>Threading model:
   * <ul>
   *   <li>Upstream subscription runs on the caller's thread (or blocking executor if configured)</li>
   *   <li>Pipe reader runs on a separate thread to avoid deadlock</li>
   *   <li>Downstream processing runs on the pipe reader thread</li>
   * </ul>
   *
   * <p>Resource cleanup is guaranteed in all cases:
   * <ul>
   *   <li>Normal completion</li>
   *   <li>Error during processing</li>
   *   <li>Downstream cancellation</li>
   *   <li>Executor failure to start</li>
   * </ul>
   *
   * @param upstream the upstream Multi producing byte arrays
   * @param downstreamFactory factory that creates the downstream Multi from the piped input
   * @return a Multi that streams data from upstream through downstream
   */
  private Multi<byte[]> chainMultiToMulti(
      Multi<byte[]> upstream,
      Function<Multi<byte[]>, Multi<byte[]>> downstreamFactory) {

    // Use holders for resources so they can be accessed in cleanup
    final PipedOutputStream[] pipedOutHolder = new PipedOutputStream[1];
    final PipedInputStream[] pipedInHolder = new PipedInputStream[1];

    return Multi.createFrom().<byte[]>emitter(emitter -> {
      try {
        pipedOutHolder[0] = new PipedOutputStream();
        pipedInHolder[0] = new PipedInputStream(pipedOutHolder[0], bufferSize);

        final PipedOutputStream pipedOut = pipedOutHolder[0];
        final PipedInputStream pipedIn = pipedInHolder[0];

        // Get executor for the pipe reader thread
        Executor executor = blockingExecutor.orElse(Infrastructure.getDefaultWorkerPool());

        // Start pipe reader on a separate thread to avoid deadlock
        executor.execute(() -> {
          try {
            // Create the input Multi that reads from the pipe
            Multi<byte[]> pipeReader = Multi.createFrom().generator(
                () -> pipedIn,
                (inputStream, emitterInner) -> {
                  byte[] buffer = new byte[bufferSize];
                  try {
                    int bytesRead = inputStream.read(buffer);
                    if (bytesRead == -1) {
                      emitterInner.complete();
                    } else {
                      byte[] chunk = new byte[bytesRead];
                      System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                      emitterInner.emit(chunk);
                    }
                  } catch (IOException e) {
                    emitterInner.fail(e);
                  }
                  return inputStream;
                });

            // Apply the downstream transformation and forward results
            Multi<byte[]> downstream = downstreamFactory.apply(pipeReader);

            downstream.subscribe().with(
                emitter::emit,
                error -> {
                  closeQuietly(pipedIn);
                  emitter.fail(error);
                },
                () -> {
                  closeQuietly(pipedIn);
                  emitter.complete();
                });

          } catch (Exception e) {
            closeQuietly(pipedIn);
            emitter.fail(e);
          }
        });

        // Subscribe to upstream and write to pipe
        upstream.subscribe().with(
            bytes -> {
              try {
                pipedOut.write(bytes);
                pipedOut.flush();  // Ensure data is available for reader
              } catch (IOException e) {
                emitter.fail(e);
              }
            },
            error -> {
              closeQuietly(pipedOut);
              emitter.fail(error);
            },
            () -> {
              closeQuietly(pipedOut);
            });

      } catch (IOException e) {
        // Cleanup on initialization failure
        closeQuietly(pipedOutHolder[0]);
        closeQuietly(pipedInHolder[0]);
        emitter.fail(e);
      }
    }).onCancellation().invoke(() -> {
      // Cleanup on downstream cancellation
      closeQuietly(pipedOutHolder[0]);
      closeQuietly(pipedInHolder[0]);
    });
  }

  /**
   * Closes a Closeable resource quietly, ignoring any exceptions.
   */
  private static void closeQuietly(Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (IOException ignored) {
        // Ignore - cleanup should be silent
      }
    }
  }

  // ==================== Builder ====================

  /**
   * Builder for ReactiveVersionTransformer.
   */
  public static class Builder {
    private JAXBContext jaxbContext;
    private int bufferSize = DEFAULT_BUFFER_SIZE;
    private BiFunction<Object, List<Object>, Object> eventMapper;
    private Executor blockingExecutor;

    Builder() {}

    /**
     * Sets a custom JAXB context.
     *
     * @param jaxbContext the JAXB context
     * @return this builder
     */
    public Builder jaxbContext(JAXBContext jaxbContext) {
      this.jaxbContext = jaxbContext;
      return this;
    }

    /**
     * Sets the buffer size for reading/writing.
     *
     * @param bufferSize the buffer size in bytes (default 8KB)
     * @return this builder
     */
    public Builder bufferSize(int bufferSize) {
      if (bufferSize <= 0) {
        throw new IllegalArgumentException("Buffer size must be positive");
      }
      this.bufferSize = bufferSize;
      return this;
    }

    /**
     * Sets an event mapper function using the raw BiFunction signature.
     *
     * <p>The mapper receives the event object and a list of context objects (typically empty),
     * and should return the transformed event. This is useful for enriching events with
     * additional data during conversion.
     *
     * <p>For a type-safe alternative, use {@link #eventMapper(EPCISEventMapper)} instead.
     *
     * @param eventMapper function to transform events during conversion
     * @return this builder
     */
    public Builder eventMapper(BiFunction<Object, List<Object>, Object> eventMapper) {
      this.eventMapper = eventMapper;
      return this;
    }

    /**
     * Sets a typed event mapper function.
     *
     * <p>This is the recommended way to configure event mapping as it provides
     * type safety for EPCIS events.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * EPCISEventMapper mapper = (event, context) -> {
     *     // Transform event...
     *     return event;
     * };
     *
     * ReactiveVersionTransformer transformer = ReactiveVersionTransformer.builder()
     *     .eventMapperTyped(mapper)
     *     .build();
     * }</pre>
     *
     * @param eventMapper typed mapper to transform events during conversion
     * @return this builder
     * @see EPCISEventMapper
     */
    public Builder eventMapperTyped(EPCISEventMapper eventMapper) {
      this.eventMapper = eventMapper != null ? eventMapper.toBiFunction() : null;
      return this;
    }

    /**
     * Sets the executor for blocking operations.
     *
     * <p>XML parsing (StAX) and XSLT transformations are inherently blocking operations.
     * When using this converter in async runtimes (Quarkus, Vert.x), configure a blocking
     * executor to avoid EventLoop starvation:
     *
     * <pre>{@code
     * ReactiveVersionTransformer transformer = ReactiveVersionTransformer.builder()
     *     .blockingExecutor(Infrastructure.getDefaultWorkerPool())
     *     .build();
     * }</pre>
     *
     * <p>If not set, blocking operations will run on the subscription thread which may
     * be the EventLoop thread in async contexts - this can cause performance issues.
     *
     * <p><strong>Warning:</strong> Using a direct/synchronous executor (like {@code Runnable::run})
     * defeats the purpose of blocking isolation. Use a real thread pool executor.
     *
     * @param executor the executor for blocking operations
     * @return this builder
     */
    public Builder blockingExecutor(Executor executor) {
      Objects.requireNonNull(executor, "Blocking executor cannot be null");
      // Warn if using a direct/synchronous executor
      String className = executor.getClass().getName();
      if (className.contains("Direct") || className.contains("Synchronous") ||
          className.contains("SameThread") || className.contains("CallerRuns")) {
        LOG.log(Level.WARNING, "Direct/synchronous executor configured for blocking operations. " +
                 "This defeats blocking isolation - use a real thread pool executor instead.");
      }
      this.blockingExecutor = executor;
      return this;
    }

    /**
     * Builds the ReactiveVersionTransformer.
     *
     * @return new transformer instance
     * @throws RuntimeException if initialization fails
     */
    public ReactiveVersionTransformer build() {
      try {
        return new ReactiveVersionTransformer(this);
      } catch (JAXBException e) {
        throw new RuntimeException("Failed to initialize ReactiveVersionTransformer", e);
      }
    }
  }
}
