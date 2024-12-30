/*
 * Copyright 2022-2024 benelog GmbH & Co. KG
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package io.openepcis.converter.collector;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.collector.context.ContextProcessorApp;
import io.openepcis.converter.collector.context.api.ContextHandler;
import io.openepcis.converter.common.GS1FormatSupport;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.model.epcis.util.DefaultJsonSchemaNamespaceURIResolver;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import static io.openepcis.constants.EPCIS.*;

/**
 * Class that implements the interface EPCISEventsCollector to create the final JSON file with all
 * converted events from XML - JSON format.
 *
 * <p>start : Method to create the header information for the JSON-LD file. collect : Method to
 * store event information based on user provided OutputStream type. end : Method to close all the
 * JSON-LD header objects that were created in the start method.
 */
public class JsonEPCISEventCollector implements EPCISEventCollector<OutputStream> {
  private final OutputStream stream;
  private final JsonGenerator jsonGenerator;
  private boolean jsonEventSeparator;
  private boolean isEPCISDocument;
  private String subscriptionID;
  private String queryName;
  private final DefaultJsonSchemaNamespaceURIResolver namespaceResolver = DefaultJsonSchemaNamespaceURIResolver.getContext();

  public JsonEPCISEventCollector(OutputStream stream) {
    this.stream = stream;

    // Create the final JSON-LD with Header and event information
    try {
      jsonGenerator = new JsonFactory().createGenerator(this.stream).useDefaultPrettyPrinter();
      jsonEventSeparator = false;
    } catch (IOException e) {
      throw new FormatConverterException("Exception during XML-JSON-LD conversion, Error occurred during the creation of JsonGenerator object " + e, e);
    }
  }

  @Override
  public void collect(Object event) {
    try {
      // Add the comma separator between each of the event as the number of events are unknown "," will be added before writing the next event
      if (jsonEventSeparator) {
        jsonGenerator.writeRaw(",");
      }

      // Add each of the converted event into the EventList of the JSON file that is created in start method
      jsonGenerator.writeRaw(event.toString());
      jsonEventSeparator = true;
    } catch (IOException e) {
      throw new FormatConverterException("Exception during XML-JSON-LD conversion, Error occurred during writing of the events to JsonGenerator: " + e, e);
    }
  }

  @Override
  public OutputStream get() {
    return stream;
  }

  @Override
  public void start(Map<String, String> context) {
    try {
      // create Outermost JsonObject
      jsonGenerator.writeStartObject();

      // Write the info related to Context element in JSON
      jsonGenerator.writeFieldName(CONTEXT);
      jsonGenerator.writeStartArray(); // Start of context array

      // Get all document level namespaces for adding within context
      final Map<String, String> allNamespaces = namespaceResolver.getAllNamespaces();

      // Use the delegation approach to add either Default context from DefaultContext or add custom context such as GS1 Egypt or other
      final ContextHandler customLogicProvider = ContextProcessorApp.resolveForJsonConversion(allNamespaces, GS1FormatSupport.getExtension());
      customLogicProvider.buildJsonContext(jsonGenerator, allNamespaces);

      jsonGenerator.writeEndArray(); // End of context array

      // Write Other header fields of JSON
      jsonGenerator.writeStringField(TYPE, isEPCISDocument ? EPCIS_DOCUMENT : EPCIS_QUERY_DOCUMENT);

      // Write schema version and other attributes within XML Header
      context.forEach(
              (key, value) -> {
                try {
                  if (key.equalsIgnoreCase(SCHEMA_VERSION)) {
                    jsonGenerator.writeStringField(key, "2.0");
                  } else {
                    jsonGenerator.writeStringField(key, value);
                  }
                } catch (IOException e) {
                  throw new FormatConverterException("Exception during XML-JSON-LD conversion, Error occurred during the addition of attributes: " + e, e);
                }
              });

      // Start epcisBody object
      jsonGenerator.writeFieldName(EPCIS_BODY_IN_CAMEL_CASE);
      jsonGenerator.writeStartObject();

      // Add additional wrapper tags for EPCISQueryDocument
      if (!isEPCISDocument) {
        jsonGenerator.writeFieldName(QUERY_RESULTS_IN_CAMEL_CASE);
        jsonGenerator.writeStartObject();

        if (!StringUtils.isBlank(subscriptionID)) {
          jsonGenerator.writeStringField(SUBSCRIPTION_ID, subscriptionID);
        }

        if (!StringUtils.isBlank(queryName)) {
          jsonGenerator.writeStringField(QUERY_NAME, queryName);
        }

        jsonGenerator.writeFieldName(RESULTS_BODY_IN_CAMEL_CASE);
        jsonGenerator.writeStartObject();
      }

      // Start eventList
      jsonGenerator.writeFieldName(EVENT_LIST_IN_CAMEL_CASE);
      jsonGenerator.writeStartArray();
    } catch (IOException e) {
      throw new FormatConverterException("Exception during XML-JSON-LD conversion, Error occurred during the creation of JSON-LD events file: " + e, e);
    }
  }

  @Override
  public void end() {
    try {
      jsonGenerator.writeEndArray(); // End the eventList array

      // Close additional wrapper tags for EPCISQueryDocument
      if (!isEPCISDocument) {
        jsonGenerator.writeEndObject(); // End resultsBody
        jsonGenerator.writeEndObject(); // End queryResults
      }

      jsonGenerator.writeEndObject(); // End epcisBody
      jsonGenerator.writeEndObject(); // End whole json file
    } catch (IOException e) {
      throw new FormatConverterException("Exception during XML-JSON-LD conversion, Error occurred during the closing of JSON-LD events file: " + e, e);
    } finally {
      try {
        jsonGenerator.close();
        jsonGenerator.flush();
      } catch (Exception e) {
        // do nothing
      }
    }
  }

  @Override
  public void startSingleEvent(Map<String, String> context) {
    try {
      // create Outermost JsonObject
      jsonGenerator.writeStartObject();
      // Write the info related to Context element in JSON
      jsonGenerator.writeFieldName(CONTEXT);
      jsonGenerator.writeStartArray();
      jsonGenerator.writeString(EPCISVersion.getDefaultJSONContext());

      // Get all the stored namespaces from jsonNamespaces
      namespaceResolver
          .getEventNamespaces()
          .forEach(
              (key, value) -> {
                try {
                  jsonGenerator.writeStartObject();
                  jsonGenerator.writeStringField(value, key);
                  jsonGenerator.writeEndObject();
                } catch (IOException e1) {
                  throw new FormatConverterException("Exception during XML-JSON-LD single event conversion, Error occurred during the addition of Namespaces: " + e1, e1);
                }
              });
      jsonGenerator.writeEndArray();

      // Reset the event namespaces
      namespaceResolver.resetEventNamespaces();

      // Add comma to separate the context and serialized event
      jsonGenerator.writeRaw(",");
    } catch (IOException e) {
      throw new FormatConverterException("Exception during XML-JSON-LD single event conversion : " + e, e);
    }
  }

  @Override
  public void collectSingleEvent(Object event) {
    try {
      // Add converted event into the JSON object created using startSingleEvent
      jsonGenerator.writeRaw(event.toString(), 1, event.toString().length() - 2);
    } catch (IOException e) {
      throw new FormatConverterException("Exception during XML-JSON-LD single event conversion, Error occurred during writing of the events to JsonGenerator: " + e, e);
    }
  }

  @Override
  public void endSingleEvent() {
    try {
      jsonGenerator.writeEndObject(); // End whole json file
      jsonGenerator.close();
      jsonGenerator.flush();
    } catch (IOException e) {
      throw new FormatConverterException("Exception during XML-JSON-LD single event conversion : " + e, e);
    }
  }

  @Override
  public void setIsEPCISDocument(boolean isEPCISDocument) {
    this.isEPCISDocument = isEPCISDocument;
  }

  @Override
  public void setSubscriptionID(String subscriptionID) {
    this.subscriptionID = subscriptionID;
  }

  @Override
  public void setQueryName(String queryName) {
    this.queryName = queryName;
  }

  @Override
  public boolean isEPCISDocument() {
    return this.isEPCISDocument;
  }

  @Override
  public void close() throws Exception {
    if (jsonGenerator != null && !jsonGenerator.isClosed()) {
      jsonGenerator.close();
    }
  }
}
