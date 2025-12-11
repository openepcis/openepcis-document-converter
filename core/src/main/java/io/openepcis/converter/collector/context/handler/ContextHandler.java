package io.openepcis.converter.collector.context.handler;

import com.fasterxml.jackson.core.JsonGenerator;
import io.openepcis.model.epcis.util.ConversionNamespaceContext;
import java.net.URI;
import java.util.Map;

/**
 * Defines the contract for handling context and namespace resolution during JSON ↔ XML conversion.
 * Implementations can customize behavior for specific contexts.
 *
 * <p>Handlers are processed in priority order (lowest number first). The default priority is 0.
 * Use {@link Integer#MAX_VALUE} for fallback handlers that should only match when no other handler does.
 */
public interface ContextHandler {

  // Builds the JSON-LD @context during XML → JSON conversion.
  void buildJsonContext(final JsonGenerator jsonGenerator, final Map<String, String> namespaces);

  // Populates XML namespaces during JSON → XML conversion.
  void populateXmlNamespaces(final ConversionNamespaceContext namespaceContext);

  // Determines if the handler is applicable for JSON context or XML namespace processing.
  boolean isContextHandler(final Map<String, String> namespaces);

  // get map of context urls
  default Map<String, URI> getContextUrls() {
    return Map.of();
  }

  /**
   * Returns the priority of this handler. Lower numbers indicate higher priority.
   * Handlers are sorted by priority before processing, so lower-priority handlers
   * are checked first.
   *
   * <p>Default is 0. Use {@link Integer#MAX_VALUE} for fallback/catch-all handlers.
   *
   * @return the priority value (lower = higher priority)
   */
  default int getPriority() {
    return 0;
  }
}
