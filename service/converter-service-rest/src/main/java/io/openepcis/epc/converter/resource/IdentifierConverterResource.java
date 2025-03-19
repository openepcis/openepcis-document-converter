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
package io.openepcis.epc.converter.resource;

import io.openepcis.converter.common.IdentifierConverterUtil;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.model.rest.ProblemResponseBody;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@RegisterForReflection
@Path("/api")
@Tag(
    name = "Identifier Converter",
    description =
        "Convert EPCIS instance or class identifier from URN to digital link WebURI and vice versa.")
public class IdentifierConverterResource {

  // Method to convert the URN identifier into Web URI identifier
  @Operation(
      summary =
          "Convert EPCIS instance or class identifier from URN to digital link WebURI format.")
  @Path(value = "/convert/identifier/web-uri")
  @POST
  @Consumes(MediaType.TEXT_PLAIN)
  @Produces(MediaType.TEXT_PLAIN)
  @RequestBody(
      description = "Convert EPCIS instance or class identifier from URN to WebURI.",
      content =
          @Content(
              schema = @Schema(implementation = String.class),
              mediaType = MediaType.TEXT_PLAIN,
              examples = {
                @ExampleObject(
                    name = "Instance Identifier",
                    value = "urn:epc:id:sgtin:4068194.000000.9999",
                    description = "Example Instance Identifier."),
                @ExampleObject(
                    name = "Class Identifier",
                    value = "urn:epc:idpat:sgtin:4068194.000000.*",
                    description = "Example Class Identifier.")
              }))
  @APIResponses(
      value = {
        @APIResponse(
            responseCode = "200",
            description = "OK: Identifiers from URN to WebURI converted successfully.",
            content =
                @Content(
                    schema = @Schema(type = SchemaType.STRING),
                    mediaType = MediaType.TEXT_PLAIN,
                    example = "https://id.gs1.org/01/04068194000004/21/9999")),
        @APIResponse(
            responseCode = "400",
            description = "Bad Request: Input URN identifier contains missing/invalid information.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "401",
            description =
                "Unauthorized: Unable to convert URN identifier as request contains missing/invalid authorization.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "404",
            description =
                "Not Found: Unable to convert URN identifier as the requested resource not found.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "406",
            description =
                "Not Acceptable: Unable to convert URN identifier as server cannot find content confirming request.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "500",
            description =
                "Internal Server Error: Unable to convert Identifier URN as server encountered problem.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class)))
      })
  public Uni<String> convertToWebURI(String urn) throws FormatConverterException {
    return Uni.createFrom().item(IdentifierConverterUtil.toWebURI(urn));
  }

  // Method to convert the Web URI identifier into URN identifier
  @Operation(
      summary =
          "Convert EPCIS instance or class identifier from digital link WebURI to URN format.")
  // @io.quarkus.vertx.web.Route(path = "/convert/identifier/urn", methods =
  // io.quarkus.vertx.web.Route.HttpMethod.POST, order = 2)
  @Path(value = "/convert/identifier/urn")
  @POST
  @Consumes(MediaType.TEXT_PLAIN)
  @Produces(MediaType.APPLICATION_JSON)
  @RequestBody(
      description = "Convert instance or class identifiers from WebURI -> URN",
      content =
          @Content(
              schema = @Schema(implementation = String.class),
              mediaType = MediaType.TEXT_PLAIN,
              examples = {
                @ExampleObject(
                    name = "Instance Identifier",
                    value = "https://id.gs1.org/01/04068194000004/21/1234",
                    description = "Example Instance Identifier."),
                @ExampleObject(
                    name = "Class Identifier",
                    value = "https://id.gs1.org/01/04068194000004",
                    description = "Example Class Identifier.")
              }))
  @APIResponses(
      value = {
        @APIResponse(
            responseCode = "200",
            description = "OK: Identifiers from WebURI to URN converted successfully.",
            content =
                @Content(
                    schema = @Schema(type = SchemaType.OBJECT),
                    mediaType = MediaType.APPLICATION_JSON,
                    example =
                        """
                                            {
                                              "gtin": "4068194000004",
                                              "asURN": "urn:epc:id:sgtin:4068194.000000.1234",
                                              "serial": "1234",
                                              "asCaptured": "https://id.gs1.org/01/04068194000004/21/1234",
                                              "canonicalDL": "https://id.gs1.org/01/04068194000004/21/1234"
                                            }""")),
        @APIResponse(
            responseCode = "400",
            description =
                "Bad Request: Input WebURI identifier contains missing/invalid information.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "401",
            description =
                "Unauthorized: Unable to convert WebURI identifier as request contains missing/invalid authorization.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "404",
            description =
                "Not Found: Unable to convert WebURI identifier as the requested resource not found.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "406",
            description =
                "Not Acceptable: Unable to convert WebURI identifier as server cannot find content confirming request.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "500",
            description =
                "Internal Server Error: Unable to convert WebURI identifier as server encountered problem.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class)))
      })
  public Uni<Map<String, String>> convertToURN(final String uri) throws FormatConverterException {
    return Uni.createFrom().item(IdentifierConverterUtil.toURN(uri));
  }
}
