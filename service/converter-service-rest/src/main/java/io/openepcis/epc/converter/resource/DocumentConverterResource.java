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

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.Conversion;
import io.openepcis.converter.VersionTransformer;
import io.openepcis.converter.common.GS1FormatSupport;
import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.converter.reactive.ReactiveConversionSource;
import io.openepcis.converter.reactive.ReactiveVersionTransformer;
import io.openepcis.epc.converter.util.GS1FormatProvider;
import io.openepcis.model.epcis.EPCISDocument;
import io.openepcis.model.epcis.EPCISEvent;
import io.openepcis.model.epcis.exception.UnsupportedMediaTypeException;
import io.openepcis.model.rest.ProblemResponseBody;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.context.api.NamedInstance;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestResponse;

@RegisterForReflection
@Path("/api")
@Tag(
    name = "Format Converter",
    description = "Convert EPCIS document or single event from XML to JSON/JSON-LD and vice versa.")
public class DocumentConverterResource {

  @Inject VersionTransformer versionTransformer;

  @Inject ReactiveVersionTransformer reactiveVersionTransformer;

  @Inject GS1FormatProvider gs1FormatProvider;

  @Inject
  @NamedInstance("DocumentConverterResourceExecutor")
  ManagedExecutor managedExecutor;

  // Method to convert the input XML EPCIS events into JSON EPCIS events
  @Operation(
      summary = "Convert EPCIS document or event from 1.2/2.0 XML to EPCIS 2.0 JSON/JSON-LD.")
  @Path("/convert/json/2.0")
  @POST
  @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, "application/ld+json"})
  @Produces(MediaType.APPLICATION_JSON)
  @RequestBody(
      description =
          "Convert EPCIS document or single event from XML version 1.2 or 2.0 or JSON/JSON-LD 2.0 to EPCIS 2.0 JSON/JSON-LD.",
      content = {
        @Content(
            mediaType = MediaType.APPLICATION_XML,
            schema = @Schema(implementation = EPCISDocument.class),
            examples = {
              @ExampleObject(
                  name = "EPCIS 1.2 XML document",
                  ref = "xml1.2Document",
                  description = "Example EPCIS 1.2 document"),
              @ExampleObject(
                  name = "EPCIS 2.0 XML document",
                  ref = "xmlDocument",
                  description = "Example EPCIS 2.0 document in XML format.")
            }),
        @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = EPCISDocument.class),
            examples = {
              @ExampleObject(
                  name = "EPCIS 2.0 JSON document",
                  ref = "jsonDocument",
                  description = "Example EPCIS 2.0 document in JSON format.")
            }),
        @Content(
            mediaType = "application/ld+json",
            schema = @Schema(implementation = EPCISDocument.class),
            examples = {
              @ExampleObject(
                  name = "EPCIS 2.0 JSON document",
                  ref = "jsonDocument",
                  description = "Example EPCIS 2.0 document in JSON format.")
            })
      })
  @APIResponses(
      value = {
        @APIResponse(
            responseCode = "200",
            description = "OK: Converted to EPCIS 2.0 JSON/JSON-LD successfully.",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = EPCISDocument.class),
                    examples = {
                      @ExampleObject(
                          name = "EPCIS 2.0 JSON document",
                          ref = "jsonDocument",
                          description = "Example EPCIS 2.0 document in JSON format.")
                    })),
        @APIResponse(
            responseCode = "400",
            description =
                "Bad Request: Input EPCIS document or single event contains missing/invalid information.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "401",
            description =
                "Unauthorized: Unable to convert document or single event as request contains missing/invalid authorization.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "404",
            description =
                "Not Found: Unable to convert document or single event as the requested resource not found.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "406",
            description =
                "Not Acceptable: Unable to convert document or single event as server cannot find content confirming request.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "500",
            description =
                "Internal Server Error: Unable to convert document or single event as server encountered problem.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class)))
      })
  public StreamingOutput convertToJson_2_0(
      final InputStream inputDocument,
      @Parameter(
              name = "GS1-EPC-Format",
              description = ParameterDescription.GS1_EPC_FORMAT,
              schema =
                  @Schema(
                      type = SchemaType.STRING,
                      enumeration = {
                        "No_Preference",
                        "Always_GS1_Digital_Link",
                        "Always_EPC_URN",
                        "Never_Translates"
                      }),
              in = ParameterIn.HEADER)
          @RestHeader(value = "GS1-EPC-Format")
          String epcFormat,
      @RestHeader(value = "GS1-Extensions") String gs1Extensions,
      @Context HttpHeaders httpHeaders) {

    final MediaType mediaType = httpHeaders.getMediaType();

    if (mediaType == null || !GS1FormatSupport.isValidMediaType(mediaType)) {
      throw new UnsupportedMediaTypeException("Unsupported media type: " + mediaType);
    }

    final EPCISFormat fromFormat = GS1FormatSupport.getEPCISFormat(mediaType);

    // For JSON-LD input, version is always 2.0.0
    // For XML input, we need to detect the version
    final BufferedInputStream bufferedInput = new BufferedInputStream(inputDocument, 1024 * 1024);
    final EPCISVersion fromVersion;
    try {
      fromVersion = EPCISFormat.JSON_LD.equals(fromFormat)
          ? EPCISVersion.VERSION_2_0_0
          : versionTransformer.versionDetector(bufferedInput);
    } catch (IOException e) {
      throw new FormatConverterException("Failed to detect document version", e);
    }

    // Build conversion spec with detected version
    final Conversion conversion = Conversion.builder()
        .fromMediaType(fromFormat)
        .fromVersion(fromVersion)
        .toMediaType(EPCISFormat.JSON_LD)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    // If GS1 format headers are provided, use mapWith to apply header preferences.
    // Otherwise, let VersionTransformer's internal defaults apply (Digital Link for 2.0).
    final VersionTransformer transformer = (epcFormat != null)
        ? versionTransformer.mapWith(GS1FormatSupport.createMapper(gs1FormatProvider.getFormatPreference()), gs1Extensions)
        : versionTransformer;

    // Use VersionTransformer to perform conversion
    return output -> {
      try (InputStream result = transformer.performConversion(bufferedInput, conversion)) {
        result.transferTo(output);
      } catch (IOException e) {
        throw new FormatConverterException("Failed to write JSON output", e);
      }
    };
  }

  // Method to convert the input JSON 2.0 EPCIS events into XML 2.0 EPCIS events
  @Operation(
      summary =
          "Convert EPCIS document or single event from XML version 1.2 or 2.0 or JSON/JSON-LD 2.0 to EPCIS 2.0 XML.")
  @Path("/convert/xml/2.0")
  @POST
  @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, "application/ld+json"})
  @Produces({MediaType.APPLICATION_XML})
  @RequestBody(
      description =
          "Convert EPCIS document or single event from XML version 1.2 or 2.0 or JSON/JSON-LD 2.0 to EPCIS 2.0 XML.",
      content = {
        @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = EPCISDocument.class),
            examples = {
              @ExampleObject(
                  name = "EPCIS 2.0 JSON document",
                  ref = "jsonDocument",
                  description = "Example EPCIS 2.0 document in JSON format.")
            }),
        @Content(
            mediaType = MediaType.APPLICATION_XML,
            schema = @Schema(implementation = EPCISDocument.class),
            examples = {
              @ExampleObject(
                  name = "EPCIS 1.2 XML document",
                  ref = "xml1.2Document",
                  description = "Example EPCIS 1.2 document"),
              @ExampleObject(
                  name = "EPCIS 2.0 XML document",
                  ref = "xmlDocument",
                  description = "Example EPCIS 2.0 document in XML format.")
            }),
        @Content(
            mediaType = "application/ld+json",
            schema = @Schema(implementation = EPCISDocument.class),
            examples = {
              @ExampleObject(
                  name = "EPCIS 2.0 JSON document",
                  ref = "jsonDocument",
                  description = "Example EPCIS 2.0 document in JSON format.")
            })
      })
  @APIResponses(
      value = {
        @APIResponse(
            responseCode = "200",
            description = "OK: Converted to EPCIS 2.0 XML successfully.",
            content =
                @Content(
                    schema = @Schema(implementation = EPCISDocument.class),
                    examples =
                        @ExampleObject(
                            name = "EPCIS 2.0 XML document",
                            ref = "xmlDocument",
                            description = "Example EPCIS 2.0 document in XML format."))),
        @APIResponse(
            responseCode = "400",
            description =
                "Bad Request: Input EPCIS document or single event contains missing/invalid information.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "401",
            description =
                "Unauthorized: Unable to convert document or single event as request contains missing/invalid authorization.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "404",
            description =
                "Not Found: Unable to convert document or single event as the requested resource not found.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "406",
            description =
                "Not Acceptable: Unable to convert EPCIS document or single event as server cannot find content confirming request.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "500",
            description =
                "Internal Server Error: Unable to convert document or single event as server encountered problem.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class)))
      })
  public StreamingOutput convertToXml_2_0(
      final InputStream inputDocument,
      @Parameter(
              name = "GS1-EPC-Format",
              description = ParameterDescription.GS1_EPC_FORMAT,
              schema =
                  @Schema(
                      type = SchemaType.STRING,
                      enumeration = {
                        "No_Preference",
                        "Always_GS1_Digital_Link",
                        "Always_EPC_URN",
                        "Never_Translates"
                      }),
              in = ParameterIn.HEADER)
          @RestHeader(value = "GS1-EPC-Format")
          String epcFormat,
      @Parameter(
              name = "GS1-CBV-XML-Format",
              description = ParameterDescription.GS1_CBV_XML_FORMAT,
              schema =
                  @Schema(
                      type = SchemaType.STRING,
                      enumeration = {
                        "No_Preference",
                        "Always_Web_URI",
                        "Always_URN",
                        "Never_Translates"
                      }),
              in = ParameterIn.HEADER)
          @RestHeader(value = "GS1-CBV-XML-Format")
          String cbvFormat,
      @RestHeader(value = "GS1-Extensions") String gs1Extensions,
      @Context HttpHeaders httpHeaders) {

    final MediaType mediaType = httpHeaders.getMediaType();

    if (mediaType == null || !GS1FormatSupport.isValidMediaType(mediaType)) {
      throw new UnsupportedMediaTypeException("Unsupported media type: " + mediaType);
    }

    final EPCISFormat fromFormat = GS1FormatSupport.getEPCISFormat(mediaType);

    // For JSON-LD input, version is always 2.0.0
    // For XML input, we need to detect the version
    final BufferedInputStream bufferedInput = new BufferedInputStream(inputDocument, 1024 * 1024);
    final EPCISVersion fromVersion;
    try {
      fromVersion = EPCISFormat.JSON_LD.equals(fromFormat)
          ? EPCISVersion.VERSION_2_0_0
          : versionTransformer.versionDetector(bufferedInput);
    } catch (IOException e) {
      throw new FormatConverterException("Failed to detect document version", e);
    }

    // Build conversion spec with detected version
    final Conversion conversion = Conversion.builder()
        .fromMediaType(fromFormat)
        .fromVersion(fromVersion)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_2_0_0)
        .build();

    // If GS1 format headers are provided, use mapWith to apply header preferences.
    // Otherwise, let VersionTransformer's internal defaults apply (Digital Link for 2.0).
    final VersionTransformer transformer = (epcFormat != null || cbvFormat != null)
        ? versionTransformer.mapWith(GS1FormatSupport.createMapper(gs1FormatProvider.getFormatPreference()), gs1Extensions)
        : versionTransformer;

    // Use VersionTransformer to perform conversion
    return output -> {
      try (InputStream result = transformer.performConversion(bufferedInput, conversion)) {
        result.transferTo(output);
      } catch (IOException e) {
        throw new FormatConverterException("Failed to write XML output", e);
      }
    };
  }

  // Method to convert the input JSON 2.0 EPCIS events into XML 1.2 EPCIS events
  @Operation(
      summary =
          "Convert EPCIS document or single event from XML version 1.2 or 2.0 or JSON/JSON-LD 2.0 to EPCIS 1.2 XML.")
  @Path("/convert/xml/1.2")
  @POST
  @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, "application/ld+json"})
  @Produces(MediaType.APPLICATION_XML)
  @RequestBody(
      description =
          "Convert EPCIS document or single event from XML version 1.2 or 2.0 or JSON/JSON-LD 2.0 to EPCIS 1.2 XML.",
      content = {
        @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = EPCISDocument.class),
            examples = {
              @ExampleObject(
                  name = "EPCIS 2.0 JSON document",
                  ref = "jsonDocument",
                  description = "Example EPCIS 2.0 document in JSON format.")
            }),
        @Content(
            mediaType = MediaType.APPLICATION_XML,
            schema = @Schema(implementation = EPCISDocument.class),
            examples = {
              @ExampleObject(
                  name = "EPCIS 2.0 XML document",
                  ref = "xmlDocument",
                  description = "Example EPCIS 2.0 document in XML format."),
              @ExampleObject(
                  name = "EPCIS 1.2 XML document",
                  ref = "xml1.2Document",
                  description = "Example EPCIS 1.2 document")
            }),
        @Content(
            mediaType = "application/ld+json",
            schema = @Schema(implementation = EPCISDocument.class),
            examples = {
              @ExampleObject(
                  name = "EPCIS 2.0 JSON document",
                  ref = "jsonDocument",
                  description = "Example EPCIS 2.0 document in JSON format.")
            })
      })
  @APIResponses(
      value = {
        @APIResponse(
            responseCode = "200",
            description = "OK: Converted to EPCIS 1.2 XML successfully.",
            content =
                @Content(
                    schema = @Schema(type = SchemaType.ARRAY, implementation = EPCISEvent.class),
                    examples =
                        @ExampleObject(
                            name = "EPCIS 1.2 XML document",
                            ref = "xml1.2Document",
                            description = "Example EPCIS 1.2 document"))),
        @APIResponse(
            responseCode = "400",
            description =
                "Bad Request: Input EPCIS JSON/JSON-LD document or EPCIS 1.2 XML or single event contains missing/invalid information.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "401",
            description =
                "Unauthorized: Unable to convert EPCIS JSON/JSON-LD document or EPCIS 1.2 XML or single event as request contains missing/invalid authorization.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "404",
            description =
                "Not Found: Unable to convert EPCIS JSON/JSON-LD document or EPCIS 1.2 XML or single event as the requested resource not found.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "406",
            description =
                "Not Acceptable: Unable to convert EPCIS JSON/JSON-LD document or EPCIS 1.2 XML or single event as server cannot find content confirming request.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class))),
        @APIResponse(
            responseCode = "500",
            description =
                "Internal Server Error: Unable to convert EPCIS JSON/JSON-LD document or EPCIS 1.2 XML or single event as server encountered problem.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class)))
      })
  public StreamingOutput convertToXml_1_2(
      final InputStream inputDocument,
      @Parameter(
              name = "GS1-EPC-Format",
              description = ParameterDescription.GS1_EPC_FORMAT,
              schema =
                  @Schema(
                      type = SchemaType.STRING,
                      enumeration = {
                        "No_Preference",
                        "Always_GS1_Digital_Link",
                        "Always_EPC_URN",
                        "Never_Translates"
                      }),
              in = ParameterIn.HEADER)
          @RestHeader(value = "GS1-EPC-Format")
          String epcFormat,
      @Parameter(
              name = "GS1-CBV-XML-Format",
              description = ParameterDescription.GS1_CBV_XML_FORMAT,
              schema =
                  @Schema(
                      type = SchemaType.STRING,
                      enumeration = {
                        "No_Preference",
                        "Always_Web_URI",
                        "Always_URN",
                        "Never_Translates"
                      }),
              in = ParameterIn.HEADER)
          @RestHeader(value = "GS1-CBV-XML-Format")
          String cbvFormat,
      @RestHeader(value = "GS1-Extensions") String gs1Extensions,
      @Context HttpHeaders httpHeaders) {

    final MediaType mediaType = httpHeaders.getMediaType();

    if (mediaType == null || !GS1FormatSupport.isValidMediaType(mediaType)) {
      throw new UnsupportedMediaTypeException("Unsupported media type: " + mediaType);
    }

    final EPCISFormat fromFormat = GS1FormatSupport.getEPCISFormat(mediaType);

    // For JSON-LD input, version is always 2.0.0
    // For XML input, we need to detect the version
    final BufferedInputStream bufferedInput = new BufferedInputStream(inputDocument, 1024 * 1024);
    final EPCISVersion fromVersion;
    try {
      fromVersion = EPCISFormat.JSON_LD.equals(fromFormat)
          ? EPCISVersion.VERSION_2_0_0
          : versionTransformer.versionDetector(bufferedInput);
    } catch (IOException e) {
      throw new FormatConverterException("Failed to detect document version", e);
    }

    // Build conversion spec with detected version
    final Conversion conversion = Conversion.builder()
        .fromMediaType(fromFormat)
        .fromVersion(fromVersion)
        .toMediaType(EPCISFormat.XML)
        .toVersion(EPCISVersion.VERSION_1_2_0)
        .build();

    // If GS1 format headers are provided, use mapWith to apply header preferences.
    // Otherwise, let VersionTransformer's internal defaults apply (no conversion for 1.2).
    final VersionTransformer transformer = (epcFormat != null || cbvFormat != null)
        ? versionTransformer.mapWith(GS1FormatSupport.createMapper(gs1FormatProvider.getFormatPreference()), gs1Extensions)
        : versionTransformer;

    // Use VersionTransformer to perform conversion
    return output -> {
      try (InputStream result = transformer.performConversion(bufferedInput, conversion)) {
        result.transferTo(output);
      } catch (IOException e) {
        throw new FormatConverterException("Failed to write XML output", e);
      }
    };
  }

  @Operation(summary = "Detect the version of provided EPCIS document.", hidden = false)
  @Path("/document/version")
  @POST
  @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
  @Produces(MediaType.APPLICATION_JSON)
  @RequestBody(
      description =
          "Convert EPCIS document or single event from JSON/JSON-LD or EPCIS 2.0 XML to EPCIS 2.0 XML.",
      content = {
        @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = EPCISDocument.class),
            examples = {
              @ExampleObject(
                  name = "EPCIS 2.0 JSON document",
                  ref = "jsonDocument",
                  description = "Example EPCIS 2.0 document in JSON format.")
            }),
        @Content(
            mediaType = MediaType.APPLICATION_XML,
            schema = @Schema(implementation = EPCISDocument.class),
            examples = {
              @ExampleObject(
                  name = "EPCIS 1.2 XML document",
                  ref = "xml1.2Document",
                  description = "Example EPCIS 1.2 document"),
              @ExampleObject(
                  name = "EPCIS 2.0 XML document",
                  ref = "xmlDocument",
                  description = "Example EPCIS 2.0 document in XML format.")
            }),
        @Content(
            mediaType = "application/ld+json",
            schema = @Schema(implementation = EPCISDocument.class),
            examples = {
              @ExampleObject(
                  name = "EPCIS 2.0 JSON document",
                  ref = "jsonDocument",
                  description = "Example EPCIS 2.0 document in JSON format.")
            })
      })
  @APIResponses(
      value = {
        @APIResponse(
            responseCode = "200",
            description = "OK: version detected",
            content =
                @Content(
                    schema = @Schema(type = SchemaType.OBJECT),
                    example =
                        """
                                  {
                                    "version": "1.2.0"
                                  }""")),
        @APIResponse(
            responseCode = "400",
            description = "Bad Request: Input EPCIS document contains missing/invalid information.",
            content = @Content(schema = @Schema(implementation = ProblemResponseBody.class)))
      })
  public Uni<RestResponse<Map<String, String>>> versionDetection(final InputStream epcisDocument)
      throws IOException {
    return Uni.createFrom()
        .completionStage(
            managedExecutor.supplyAsync(
                () -> {
                  try {
                    return versionTransformer
                        .versionDetector(new BufferedInputStream(epcisDocument, 8192))
                        .getVersion();
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                }))
        .map(version -> Map.of("version", version))
        .map(response -> RestResponse.ok(response))
        .onFailure()
        .transform(t -> t.getCause());
  }
}
