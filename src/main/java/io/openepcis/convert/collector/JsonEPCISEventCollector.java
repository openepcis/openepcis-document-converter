/*
 * Copyright 2022 benelog GmbH & Co. KG
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
package io.openepcis.convert.collector;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import io.openepcis.constants.EPCIS;
import io.openepcis.convert.exception.FormatConverterException;
import io.openepcis.model.epcis.util.DefaultJsonSchemaNamespaceURIResolver;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

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

  public JsonEPCISEventCollector(OutputStream stream) {
    this.stream = stream;
    // Create the final JSON-LD with Header and event information
    try {
      jsonGenerator = new JsonFactory().createGenerator(this.stream).useDefaultPrettyPrinter();
      jsonEventSeparator = false;
    } catch (IOException e) {
      throw new FormatConverterException(
          "Exception during XML-JSON-LD conversion, Error occurred during the creation of JsonGenerator object "
              + e);
    }
  }

  @Override
  public void collect(Object event) {
    try {
      // Add the comma separator between each of the event as the number of events are unknown ","
      // will be added before writing the next event
      if (jsonEventSeparator) {
        jsonGenerator.writeRaw(",");
      }

      // Add each of the converted event into the EventList of the JSON file that is created in
      // start method
      jsonGenerator.writeRaw(event.toString());
      jsonEventSeparator = true;
    } catch (IOException e) {
      throw new FormatConverterException(
          "Exception during XML-JSON-LD conversion, Error occurred during writing of the events to JsonGenerator: "
              + e);
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

      // Write Other header fields of JSON
      jsonGenerator.writeStringField(EPCIS.TYPE, EPCIS.EPCIS_DOCUMENT);

      // Write schema version and other attributes within XML Header
      context.forEach(
          (key, value) -> {
            try {
              jsonGenerator.writeStringField(key, value);
            } catch (IOException e) {
              throw new FormatConverterException(
                  "Exception during XML-JSON-LD conversion, Error occurred during the addition of attributes: "
                      + e);
            }
          });

      // Start epcisBody object
      jsonGenerator.writeFieldName(EPCIS.EPCIS_BODY_IN_LOWER_CASE);
      jsonGenerator.writeStartObject();

      // Start eventList
      jsonGenerator.writeFieldName(EPCIS.EVENT_LIST_IN_LOWER_CASE);
      jsonGenerator.writeStartArray();
    } catch (IOException e) {
      throw new FormatConverterException(
          "Exception during XML-JSON-LD conversion, Error occurred during the creation of JSON-LD events file: "
              + e);
    }
  }

  @Override
  public void end() {
    try {
      jsonGenerator.writeEndArray(); // End the eventList array
      jsonGenerator.writeEndObject(); // End epcisBody

      // Write the info related to Context element in JSON
      jsonGenerator.writeFieldName(EPCIS.CONTEXT);
      jsonGenerator.writeStartArray();
      jsonGenerator.writeString(EPCIS.DEFAULT_CONTEXT);

      // Modify the Namespaces so trailing / or : is added and default values are removed
      DefaultJsonSchemaNamespaceURIResolver.getInstance().modifyNamespaces();
      System.out.println(
          DefaultJsonSchemaNamespaceURIResolver.getInstance().getModifiedNamespace());
      // Get all the stored namespaces from jsonNamespaces
      DefaultJsonSchemaNamespaceURIResolver.getInstance()
          .getModifiedNamespace()
          .forEach(
              (key, value) -> {
                try {
                  jsonGenerator.writeStartObject();
                  jsonGenerator.writeStringField(value, key);
                  jsonGenerator.writeEndObject();
                } catch (IOException e1) {
                  throw new FormatConverterException(
                      "Exception during XML-JSON-LD conversion, Error occurred during the addition of Namespaces: "
                          + e1);
                }
              });
      jsonGenerator.writeEndArray();
      jsonGenerator.writeEndObject(); // End whole json file
    } catch (IOException e) {
      throw new FormatConverterException(
          "Exception during XML-JSON-LD conversion, Error occurred during the closing of JSON-LD events file: "
              + e);
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
      jsonGenerator.writeFieldName(EPCIS.CONTEXT);
      jsonGenerator.writeStartArray();
      jsonGenerator.writeString(EPCIS.DEFAULT_CONTEXT);

      // Modify the Namespaces so trailing / or : is added and default values are removed
      DefaultJsonSchemaNamespaceURIResolver.getInstance().modifyNamespaces();

      // Get all the stored namespaces from jsonNamespaces
      DefaultJsonSchemaNamespaceURIResolver.getInstance()
          .getModifiedNamespace()
          .forEach(
              (key, value) -> {
                try {
                  jsonGenerator.writeStartObject();
                  jsonGenerator.writeStringField(value, key);
                  jsonGenerator.writeEndObject();
                } catch (IOException e1) {
                  throw new FormatConverterException(
                      "Exception during XML-JSON-LD single event conversion, Error occurred during the addition of Namespaces: "
                          + e1);
                }
              });
      jsonGenerator.writeEndArray();

      // Add comma to separate the context and serialized event
      jsonGenerator.writeRaw(",");
    } catch (IOException e) {
      throw new FormatConverterException(
          "Exception during XML-JSON-LD single event conversion : " + e);
    }
  }

  @Override
  public void collectSingleEvent(Object event) {
    try {
      // Add converted event into the JSON object created using startSingleEvent
      jsonGenerator.writeRaw(event.toString(), 1, event.toString().length() - 2);
    } catch (IOException e) {
      throw new FormatConverterException(
          "Exception during XML-JSON-LD single event conversion, Error occurred during writing of the events to JsonGenerator: "
              + e);
    }
  }

  @Override
  public void endSingleEvent() {
    try {
      jsonGenerator.writeEndObject(); // End whole json file
      jsonGenerator.close();
      jsonGenerator.flush();
    } catch (IOException e) {
      throw new FormatConverterException(
          "Exception during XML-JSON-LD single event conversion : " + e);
    }
  }
}
