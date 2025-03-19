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
package io.openepcis.converter.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.openepcis.constants.EPCIS;
import io.openepcis.converter.exception.FormatConverterException;
import jakarta.ws.rs.ProcessingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.xml.sax.SAXException;

/**
 * Class to validate each of the converted event against respective events XSD or JSON-Schema file
 * which is stored in resources folder. If EPCIS event does not adhere to XSD/JSON-Schema then
 * respective information's are shown in Log but the information will be added to final
 * OutputStream. If EPCIS event adheres to XSD/JSON-Schema then no information will be logged.
 */
@Slf4j
public class EventValidator implements EPCISEventValidator {

  private final Schema xsdSchema;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JsonSchemaFactory validatorFactory =
      JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
          .yamlMapper(objectMapper)
          .build();

  public EventValidator() {
    try {
      xsdSchema =
          SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
              .newSchema(
                  new StreamSource(
                      EventValidator.class
                          .getClassLoader()
                          .getResourceAsStream("eventSchemas/EPCISEventXSD.xsd")));

    } catch (SAXException e) {
      throw new FormatConverterException(e);
    }
  }

  @Override
  public void validate(Object event) {
    // Check if the incoming event is StringWriter type if so its XML so compare with XSD
    if (event instanceof StringWriter) {
      try {
        // Create an instance of Validator
        final Validator validator = xsdSchema.newValidator();

        // Assign the event to String variable
        final String convertedEvent = event.toString();

        // Validate the event against the XSD schema
        validator.validate(new StreamSource(new ByteArrayInputStream(convertedEvent.getBytes())));

        // If validation is successful then show the message of success
        log.debug("Event adheres to EPCIS Standard XSD Schema");
      } catch (Exception ex) {
        // If validation fails then show warning message
        log.warn(
            "Event Does NOT adhere to EPCIS Standard XSD Schema. However, proceeding to next event from EventList");
      }
    } else if (event instanceof String convertedEvent) {
      // If the event is of String type then its JSON so compare with JSON Schema

      try {
        // Get the JSONNode from the Event and what type of event
        final JsonNode parent = objectMapper.readTree(convertedEvent).get(EPCIS.TYPE);

        // If the epcisEvent is not null then continue with validation
        if (parent != null && parent.textValue() != null) {

          // Get the eventType so accordingly compared with the JSONSchema
          final String epcisEvent = parent.textValue();

          String schemaFile = "";

          // Based on eventType choose different Schema file for the validation
          switch (epcisEvent) {
            case EPCIS.OBJECT_EVENT -> schemaFile = "/eventSchemas/ObjectEventSchema.json";
            case EPCIS.AGGREGATION_EVENT ->
                schemaFile = "/eventSchemas/AggregationEventSchema.json";
            case EPCIS.TRANSACTION_EVENT ->
                schemaFile = "/eventSchemas/TransactionEventSchema.json";
            case EPCIS.TRANSFORMATION_EVENT ->
                schemaFile = "/eventSchemas/TransformationEventSchema.json";
            case EPCIS.ASSOCIATION_EVENT ->
                schemaFile = "/eventSchemas/AssociationEventSchema.json";
            default ->
                // If NONE of the EPCIS event type matches
                log.error(
                    "JSON event does not match any of EPCIS event. However, proceeding to next event from EventList");
          }

          // Get the schema file based on different schema and validate them
          final JsonSchema jsonSchema =
              validatorFactory.getSchema(getClass().getResourceAsStream(schemaFile));
          final Set<ValidationMessage> validationErrors =
              jsonSchema.validate(objectMapper.readValue((String) event, JsonNode.class));

          if (validationErrors.isEmpty()) {
            log.debug("Event adheres to EPCIS Standard JSON-LD Schema");
          } else {
            log.warn(
                "Event Does NOT adhere to EPCIS Standard JSON-LD Schema. However, proceeding to next event from EventList");
          }
        } else {
          log.error(
              "Converted EPCIS Event does not contain \"type\" field so cannot be validated against JSON Schema : {} "
                  + convertedEvent);
        }
      } catch (IOException | ProcessingException e) {
        throw new FormatConverterException(
            "Exception occurred during the validation of converted JSON event against the JSON-Schema : "
                + e,
            e);
      }
    }
  }
}
