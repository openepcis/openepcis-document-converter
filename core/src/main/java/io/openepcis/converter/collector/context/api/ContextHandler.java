package io.openepcis.converter.collector.context.api;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import io.openepcis.model.epcis.util.DefaultJsonSchemaNamespaceURIResolver;

import java.util.Map;

/**
 * Defines the contract for handling context and namespace resolution during JSON ↔ XML conversion. Implementations can customize behavior for specific contexts.
 */
public interface ContextHandler {

    // Builds the JSON-LD @context during XML → JSON conversion.
    void buildJsonContext(final JsonGenerator jsonGenerator, final Map<String, String> namespaces);

    // Populates XML namespaces during JSON → XML conversion.
    void populateXmlNamespaces(final JsonParser jsonParser, final DefaultJsonSchemaNamespaceURIResolver defaultNamespaceResolver);

    // Determines if the handler is applicable for JSON context processing.
    boolean supportsJsonConversion(final Map<String, String> namespaces, final String gs1Extension);

    // Determines if the handler is applicable for XML namespace processing.
    boolean supportsXmlConversion(final String context, final String gs1Extension);
}
