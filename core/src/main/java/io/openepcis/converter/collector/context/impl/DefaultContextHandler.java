package io.openepcis.converter.collector.context.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.collector.context.handler.ContextHandler;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.model.epcis.util.ConversionNamespaceContext;

import java.io.IOException;
import java.util.Map;

public class DefaultContextHandler implements ContextHandler {
    @Override
    public void buildJsonContext(
            final JsonGenerator jsonGenerator, final Map<String, String> allNamespaces) {
        try {
            // Add all custom namespaces collected during event conversion - document level namespaces
            allNamespaces.forEach(
                    (key, value) -> {
                        try {
                            jsonGenerator.writeStartObject();
                            jsonGenerator.writeStringField(value, key);
                            jsonGenerator.writeEndObject();
                        } catch (IOException e) {
                            throw new FormatConverterException("Error writing namespace: " + e.getMessage(), e);
                        }
                    });

            // Add default EPCIS JSON-LD context at the end of the context array
            jsonGenerator.writeString(EPCISVersion.getDefaultJSONContext());
        } catch (Exception e) {
            throw new FormatConverterException("Error adding default context: " + e.getMessage(), e);
        }
    }

    @Override
    public void populateXmlNamespaces(final ConversionNamespaceContext namespaceContext) {
        // No additional handling needed for default context.
    }

    @Override
    public boolean isContextHandler(final Map<String, String> namespaces) {
        return true;
    }

    @Override
    public int getPriority() {
        // Lowest priority - this is a fallback handler that should only be used
        // when no more specific handler matches
        return Integer.MAX_VALUE;
    }
}
