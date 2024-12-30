package io.openepcis.converter.collector.context;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.model.epcis.util.DefaultJsonSchemaNamespaceURIResolver;

import java.io.IOException;
import java.util.Map;

/**
 * Default class for adding the default GS1 EPCIS Context during the JSON conversion.
 */
public class DefaultContext implements CustomContextLogic {
    @Override
    public void jsonContextBuilder(final JsonGenerator jsonGenerator, final Map<String, String> allNamespaces) {
        try {
            jsonGenerator.writeString(EPCISVersion.getDefaultJSONContext());

            // Get all the stored namespaces from jsonNamespaces
            allNamespaces
                    .forEach((key, value) -> {
                        try {
                            jsonGenerator.writeStartObject();
                            jsonGenerator.writeStringField(value, key);
                            jsonGenerator.writeEndObject();
                        } catch (IOException e1) {
                            throw new FormatConverterException("Exception during XML-JSON-LD conversion, Error occurred during the addition of Namespaces: " + e1, e1);
                        }
                    });
        } catch (Exception e) {
            throw new FormatConverterException("Exception during addition of default context : " + e, e);
        }
    }

    // For default context nothing needs to be done as population of the namespaces done automatically in JSONParser during JSON->XML conversion.
    @Override
    public void xmlNamespacesBuilder(final JsonParser jsonParser,
                                     final DefaultJsonSchemaNamespaceURIResolver defaultJsonSchemaNamespaceURIResolver) {
        // No additional processing needed for default context.
    }
}
