package io.openepcis.converter.collector.context;

import io.openepcis.converter.collector.context.api.ContextHandler;
import io.openepcis.converter.exception.FormatConverterException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.ServiceLoader;

/**
 * Factory for resolving the appropriate {@link ContextHandler} implementation.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ContextProcessorApp {

    private static final ServiceLoader<ContextHandler> handlers = ServiceLoader.load(ContextHandler.class);

    public static ContextHandler resolveForJsonConversion(final Map<String, String> namespaces, final String gs1Extensions) {
        // Iterate and find the matching handler GS1 Egypt or Default during the XML -> JSON conversion
        for (final ContextHandler handler : handlers) {
            if (handler.supportsJsonConversion(namespaces, gs1Extensions)) {
                return handler;
            }
        }
        throw new FormatConverterException("No suitable ContextHandler found for JSON conversion");
    }

    public static ContextHandler resolveForXmlConversion(final String context, final String gs1Extensions) {
        // Iterate and find the matching handler GS1 Egypt or Default during the JSON -> XML conversion
        for (ContextHandler handler : handlers) {
            if (handler.supportsXmlConversion(context, gs1Extensions)) {
                return handler;
            }
        }
        throw new IllegalStateException("No suitable ContextHandler found for XML conversion");
    }
}
