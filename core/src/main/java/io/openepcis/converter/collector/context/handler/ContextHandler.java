package io.openepcis.converter.collector.context.handler;

import com.fasterxml.jackson.core.JsonGenerator;
import io.openepcis.model.epcis.util.DefaultJsonSchemaNamespaceURIResolver;

import java.net.URI;
import java.util.Map;

/**
 * Defines the contract for handling context and namespace resolution during JSON ↔ XML conversion. Implementations can customize behavior for specific contexts.
 */
public interface ContextHandler {

    // Builds the JSON-LD @context during XML → JSON conversion.
    void buildJsonContext(final JsonGenerator jsonGenerator, final Map<String, String> namespaces);

    // Populates XML namespaces during JSON → XML conversion.
    void populateXmlNamespaces(final DefaultJsonSchemaNamespaceURIResolver namespaceURIResolver);

    // Determines if the handler is applicable for JSON context or XML namespace processing.
    boolean isContextHandler(final Map<String, String> namespaces);

    // get map of context urls
    default Map<String, URI> getContextUrls() {
        return Map.of();
    }
}
