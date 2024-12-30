package io.openepcis.converter.collector.context;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.model.epcis.util.DefaultJsonSchemaNamespaceURIResolver;

import java.io.IOException;
import java.util.Map;

/**
 * Custom class for adding the GS1 Egypt related namespaces during the JSON conversion
 */
public class GS1EgyptContext implements CustomContextLogic {
    public static final String GS1_EGYPT_CONTEXT = "https://gs1eg.org/standards/epcis/2.0.0/epcis-context.jsonld";
    public static final String GS1_EGYPT_NAMESPACE = "http://epcis.gs1eg.org/hc/ns";
    public static final String GS1_EGYPT_PREFIX = "gs1egypthc";

    @Override
    public void jsonContextBuilder(final JsonGenerator jsonGenerator, final Map<String, String> allNamespaces) {
        try {
            jsonGenerator.writeString(GS1_EGYPT_CONTEXT);

            // Add the namespaces if they do not belong to the GS1 Egypt specific as its already part of the GS1 Egypt Context
            allNamespaces.forEach((key, value) -> {
                if (!GS1_EGYPT_NAMESPACE.equalsIgnoreCase(key) && !GS1_EGYPT_PREFIX.equalsIgnoreCase(value)) {
                    try {
                        jsonGenerator.writeStartObject();
                        jsonGenerator.writeStringField(value, key);
                        jsonGenerator.writeEndObject();
                    } catch (IOException e1) {
                        throw new FormatConverterException("Exception during XML-JSON-LD conversion, Error occurred during the addition of Namespaces for GS1 Egypt context: " + e1, e1);
                    }
                }
            });
        } catch (Exception e) {
            throw new FormatConverterException("Exception during addition of custom GS1 Egypt context : " + e, e);
        }
    }

    @Override
    public void xmlNamespacesBuilder(final JsonParser jsonParser, final DefaultJsonSchemaNamespaceURIResolver defaultJsonSchemaNamespaceURIResolver) {
        defaultJsonSchemaNamespaceURIResolver.populateDocumentNamespaces(GS1_EGYPT_NAMESPACE, GS1_EGYPT_PREFIX);
    }
}
