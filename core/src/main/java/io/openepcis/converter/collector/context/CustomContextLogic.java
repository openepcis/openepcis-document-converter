package io.openepcis.converter.collector.context;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import io.openepcis.model.epcis.util.DefaultJsonSchemaNamespaceURIResolver;

import java.util.Map;

/**
 * Interface to delegate the context addition logic to either GS1EgyptContext or DefaultContext.
 */
public interface CustomContextLogic {
    //Method to populate the Context information during the XML -> JSON conversion based on Default or GS1 Egypt context from JsonEPCISEventCollector
    void jsonContextBuilder(final JsonGenerator jsonGenerator, final Map<String, String> allNamespaces);

    //Method to populate the namespaces during JSON -> XML conversion based on Default or GS1 Egypt extension from JSONEventParser
    void xmlNamespacesBuilder(final JsonParser jsonParser, final DefaultJsonSchemaNamespaceURIResolver defaultJsonSchemaNamespaceURIResolver);
}
