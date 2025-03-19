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
package io.openepcis.converter.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.openepcis.converter.EventsConverter;
import io.openepcis.converter.collector.EPCISEventCollector;
import io.openepcis.converter.collector.EventHandler;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.model.epcis.EPCISEvent;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import javax.xml.stream.XMLStreamException;

public class JSONEventValueTransformer extends JsonEventParser implements EventsConverter {

  public JSONEventValueTransformer() {}

  private JSONEventValueTransformer(BiFunction<Object, List<Object>, Object> epcisEventMapper) {
    this.epcisEventMapper = Optional.ofNullable(epcisEventMapper);
  }

  @Override
  public void convert(
      InputStream jsonStream, EventHandler<? extends EPCISEventCollector> eventHandler)
      throws IOException, XMLStreamException, JAXBException {
    convert(jsonStream, eventHandler, null);
  }

  @Override
  public void convert(
      InputStream jsonStream,
      EventHandler<? extends EPCISEventCollector> eventHandler,
      JAXBContext jaxbContext)
      throws IOException, XMLStreamException, JAXBException {
    // Check if InputStream has some content if not then throw appropriate Exception
    validateJsonStream(jsonStream);

    // Clear the namespaces before reading the document
    defaultJsonSchemaNamespaceURIResolver.resetAllNamespaces();

    // Store the information from JSON header for creation of final XML
    final Map<String, String> contextValues = new HashMap<>();

    final AtomicInteger sequenceInEventList = new AtomicInteger(0);

    // Get the JSON Factory and parser Object
    try (JsonParser jsonParser = new JsonFactory().createParser(jsonStream)) {

      // To read the duplicate keys for User Extensions, ILMD and other elements in JSON-LD
      jsonParser.setCodec(objectMapper);

      // Check the first element is Object if not then invalid JSON throw error
      if (jsonParser.nextToken() != JsonToken.START_OBJECT) {
        throw new FormatConverterException(
            "Invalid JSON-LD file has been provided. JSON-LD file should start with the Object");
      }

      // Loop until type element to read the Context values and namespaces present in it
      collectNameSpaceAndContextValues(jsonParser, contextValues);

      try {
        EPCISEvent event = processSingleEvent(sequenceInEventList, jsonParser);

        // validate and write event to output stream
        eventHandler.collectSingleEvent(event);
      } catch (Exception e) {
        // Loop until the start of the EPCIS EventList array and prepare the XML header elements
        collectDocumentMetaData(contextValues, jsonParser, eventHandler);

        // Goto the next token
        jsonParser.nextToken();

        // this will prepare document header, epcisBody, eventList elements
        eventHandler.start(contextValues);

        // Call the method to loop until the end of the events file
        eventTraverser(jsonParser, objectMapper, null, eventHandler, false);

        // Call the End method to close all the headers
        eventHandler.end();

        // Close the JSON Parser after completing the reading of all contents
        jsonParser.close();
      }

    } catch (Exception e) {
      throw new FormatConverterException("Exception during the reading of JSON-LD file : " + e, e);
    }
    // Close JSONParser after reading all events
  }

  public final JSONEventValueTransformer mapWith(
      final BiFunction<Object, List<Object>, Object> mapper) {
    return new JSONEventValueTransformer(mapper);
  }
}
