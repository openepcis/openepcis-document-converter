package io.openepcis.converter.collector.context;

import com.fasterxml.jackson.core.JsonGenerator;
import io.openepcis.converter.collector.context.handler.ContextHandler;
import io.openepcis.converter.collector.context.impl.DefaultContextHandler;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.model.epcis.util.DefaultJsonSchemaNamespaceURIResolver;

import java.net.URI;
import java.util.*;

/**
 * Factory for resolving the appropriate {@link ContextHandler} implementation.
 */
public class ContextProcessor {
    private static ContextProcessor _instance;
    private final List<ContextHandler> handlers;

    private ContextProcessor(final List<ContextHandler> handlers) {
        this.handlers = handlers == null || handlers.isEmpty() ? new ArrayList<>():handlers;
        if (this.handlers.isEmpty()) {
            this.handlers.add(new DefaultContextHandler());
        }
    }

    public Map<String, URI> getContextUrls(){
        final Map<String, URI> map = new HashMap<>();
        handlers.forEach(c -> map.putAll(c.getContextUrls()));
        return map;
    }

    public synchronized static ContextProcessor getInstance() {
        if (_instance == null) {
            _instance = newInstance();
        }
        return _instance;
    }

    public synchronized static ContextProcessor newInstance() {
        return new ContextProcessor(ServiceLoader.load(ContextHandler.class).stream().map(ServiceLoader.Provider::get).toList());
    }

    public void addContextHandler(final ContextHandler handler) {
        handlers.add(handler);
    }

    public void resolveForJsonConversion(final JsonGenerator jsonGenerator, final Map<String, String> allNamespaces) {
        // Iterate and find the matching handler such as GS1 Egypt or Default during the XML -> JSON conversion
        for (final ContextHandler handler : handlers) {
              if (handler.isContextHandler(allNamespaces)) {
                  handler.buildJsonContext(jsonGenerator, allNamespaces);
                  return;
              }
        }
        throw new FormatConverterException("No suitable ContextHandler found for JSON conversion");
    }

    public void resolveForXmlConversion(final Map<String, String> contextNamespaces, final DefaultJsonSchemaNamespaceURIResolver namespaceURIResolver) {
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
