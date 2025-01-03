package io.openepcis.converter.collector.context;

import com.fasterxml.jackson.core.JsonGenerator;
import io.openepcis.converter.collector.context.handler.ContextHandler;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.model.epcis.util.DefaultJsonSchemaNamespaceURIResolver;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.ServiceLoader;

/**
 * Factory for resolving the appropriate {@link ContextHandler} implementation.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ContextProcessor {

    private static final ServiceLoader<ContextHandler> handlers = ServiceLoader.load(ContextHandler.class);

    public static void resolveForJsonConversion(final JsonGenerator jsonGenerator, final Map<String, String> allNamespaces) {
        // Iterate and find the matching handler such as GS1 Egypt or Default during the XML -> JSON conversion
        for (final ContextHandler handler : handlers) {
            if (handler.isContextHandler(allNamespaces)) {
                handler.buildJsonContext(jsonGenerator, allNamespaces);
                return;
            }
        }
        throw new FormatConverterException("No suitable ContextHandler found for JSON conversion");
    }

    public static void resolveForXmlConversion(final Map<String, String> contextNamespaces, final DefaultJsonSchemaNamespaceURIResolver namespaceURIResolver) {
        // Iterate and find the matching handler GS1 Egypt or Default during the JSON -> XML conversion
        for (final ContextHandler handler : handlers) {
            if (handler.isContextHandler(contextNamespaces)) {
                handler.populateXmlNamespaces(namespaceURIResolver);
                return;
            }
        }
        throw new IllegalStateException("No suitable ContextHandler found for XML conversion");
    }
}
