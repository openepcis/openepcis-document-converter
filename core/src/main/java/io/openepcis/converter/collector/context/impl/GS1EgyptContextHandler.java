package io.openepcis.converter.collector.context.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import io.openepcis.converter.collector.context.api.ContextHandler;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.model.epcis.util.DefaultJsonSchemaNamespaceURIResolver;

import java.io.IOException;
import java.util.Map;

/**
 * Custom class for adding the GS1 Egypt related namespaces during the JSON conversion
 */
public class GS1EgyptContextHandler implements ContextHandler {
    public static final String GS1_EGYPT_CONTEXT = "https://gs1eg.org/standards/epcis/2.0.0/epcis-context.jsonld";
    public static final String GS1_EGYPT_NAMESPACE = "http://epcis.gs1eg.org/hc/ns";
    public static final String GS1_EGYPT_PREFIX = "gs1egypthc";

    @Override
    public void buildJsonContext(final JsonGenerator jsonGenerator, final Map<String, String> allNamespaces) {
        try {
            jsonGenerator.writeString(GS1_EGYPT_CONTEXT);

            // Add the namespaces if they do not belong to the GS1 Egypt specific as its already part of the GS1 Egypt Context
            allNamespaces.forEach((key, value) -> {
                if (!GS1_EGYPT_NAMESPACE.equalsIgnoreCase(key) && !GS1_EGYPT_PREFIX.equalsIgnoreCase(value)) {
                    try {
                        jsonGenerator.writeStartObject();
                        jsonGenerator.writeStringField(value, key);
                        jsonGenerator.writeEndObject();
                    } catch (IOException e) {
                        throw new FormatConverterException("Error writing GS1 Egypt namespace: " + e.getMessage(), e);
                    }
                }
            });
        } catch (Exception e) {
            throw new FormatConverterException("Error adding GS1 Egypt context: " + e.getMessage(), e);
        }
    }

    @Override
    public void populateXmlNamespaces(final JsonParser jsonParser, final DefaultJsonSchemaNamespaceURIResolver defaultJsonSchemaNamespaceURIResolver) {
        defaultJsonSchemaNamespaceURIResolver.populateDocumentNamespaces(GS1_EGYPT_NAMESPACE, GS1_EGYPT_PREFIX);
    }


    @Override
    public boolean supportsJsonConversion(final Map<String, String> allNamespaces, final String gs1Extensions) {
        return allNamespaces.containsKey(GS1_EGYPT_NAMESPACE) || allNamespaces.containsValue(GS1_EGYPT_PREFIX) || GS1_EGYPT_PREFIX.equalsIgnoreCase(gs1Extensions);
    }

    @Override
    public boolean supportsXmlConversion(final String context, final String gs1Extensions) {
        return GS1_EGYPT_CONTEXT.equalsIgnoreCase(context) || GS1_EGYPT_PREFIX.equalsIgnoreCase(gs1Extensions);
    }
}
