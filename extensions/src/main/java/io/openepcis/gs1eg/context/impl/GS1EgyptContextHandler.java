package io.openepcis.gs1eg.context.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.collector.context.handler.ContextHandler;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.model.epcis.util.DefaultJsonSchemaNamespaceURIResolver;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;

/** Custom class for adding the GS1 Egypt related namespaces during the JSON conversion */
public class GS1EgyptContextHandler implements ContextHandler {
  public static final String GS1_EGYPT_AT_CONTEXT_URL =
      "https://ref.gs1eg.org/extensions/epcis/hc/2.0.0/hc-context.jsonld";
  public static final String GS1_EGYPT_2_0_NAMESPACE = "https://ref.gs1eg.org/extensions/epcis/hc/";
  public static final String GS1_EGYPT_DEFAULT_PREFIX = "gs1egypthc";

  @Override
  public void buildJsonContext(
      final JsonGenerator jsonGenerator, final Map<String, String> allNamespaces) {
    try {
      jsonGenerator.writeString(GS1_EGYPT_AT_CONTEXT_URL);

      // Add the namespaces if they do not belong to the GS1 Egypt specific as its already part of the GS1 Egypt Context
      allNamespaces.forEach(
          (namespaceURI, prefix) -> {
            if (!GS1_EGYPT_2_0_NAMESPACE.equalsIgnoreCase(namespaceURI)) {
              try {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField(prefix, namespaceURI);
                jsonGenerator.writeEndObject();
              } catch (IOException e) {
                throw new FormatConverterException(
                    "Error writing GS1 Egypt namespace: " + e.getMessage(), e);
              }
            }
          });

      // Add default EPCIS JSON-LD context at the end of the context array
      jsonGenerator.writeString(EPCISVersion.getDefaultJSONContext());
    } catch (Exception e) {
      throw new FormatConverterException("Error adding GS1 Egypt context: " + e.getMessage(), e);
    }
  }

  @Override
  public void populateXmlNamespaces(
      final DefaultJsonSchemaNamespaceURIResolver defaultJsonSchemaNamespaceURIResolver) {
    defaultJsonSchemaNamespaceURIResolver.populateDocumentNamespaces(
        GS1_EGYPT_2_0_NAMESPACE, GS1_EGYPT_DEFAULT_PREFIX);
  }

  @Override
  public boolean isContextHandler(final Map<String, String> allNamespaces) {
    return allNamespaces.containsKey(GS1_EGYPT_AT_CONTEXT_URL)
        || allNamespaces.containsKey(GS1_EGYPT_2_0_NAMESPACE);
  }

  @Override
  public Map<String, URI> getContextUrls() {
    try {
      return Map.of(
          GS1_EGYPT_AT_CONTEXT_URL,
          Objects.requireNonNull(GS1EgyptContextHandler.class.getResource("/hc-context.jsonld"))
              .toURI());
    } catch (Exception e) {
      throw new FormatConverterException(
          "Loading of GS1Egypt context file failed : " + e.getMessage(), e);
    }
  }
}
