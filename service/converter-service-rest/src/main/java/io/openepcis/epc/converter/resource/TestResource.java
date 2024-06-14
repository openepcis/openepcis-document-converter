package io.openepcis.epc.converter.resource;

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.Conversion;
import io.openepcis.converter.VersionTransformer;
import io.openepcis.converter.common.GS1FormatSupport;
import io.openepcis.converter.xml.DefaultXmlVersionTransformer;
import io.openepcis.model.epcis.EPCISDocument;
import io.openepcis.model.epcis.format.FormatPreference;
import io.openepcis.model.rest.ProblemResponseBody;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.StreamingOutput;
import org.apache.commons.io.IOUtils;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestMulti;
import org.reactivestreams.FlowAdapters;
import software.amazon.awssdk.utils.async.InputStreamConsumingPublisher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

@Path("/api")
@Tag(
        name = "Tests",
        description = "Test some")
public class TestResource {

  @Inject
  ManagedExecutor managedExecutor;
  @Inject
  VersionTransformer versionTransformer;

  DefaultXmlVersionTransformer xmlVersionTransformer = new DefaultXmlVersionTransformer(null);
  @Operation(
          summary = "Convert EPCIS document or single event from XML version 1.2 or 2.0 or JSON/JSON-LD 2.0 to EPCIS 2.0 JSON/JSON-LD.")
  @Path("/test")
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
                          description =
                                  "OK: Converted to EPCIS 2.0 XML successfully.",
                          content =
                          @Content(
                                  schema = @Schema(implementation = EPCISDocument.class),
                                  examples = @ExampleObject(
                                          name = "EPCIS 2.0 XML document",
                                          ref = "xmlDocument",
                                          description = "Example EPCIS 2.0 document in XML format.")
                          )),
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
  public RestMulti<byte[]> testSome(InputStream body) throws InterruptedException {
    xmlVersionTransformer.setExecutorService(managedExecutor);
    InputStreamConsumingPublisher publisher = new InputStreamConsumingPublisher();
    managedExecutor.execute(() -> publisher.doBlockingWrite(body));
    return RestMulti.fromMultiData(
            Multi.createFrom().publisher(
                    xmlVersionTransformer.convert12To20(FlowAdapters.toFlowPublisher(publisher))
            )
            .runSubscriptionOn(managedExecutor)
            .map(b -> b.array())).build();
  }

  @Path("/echo-test")
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
                          description =
                                  "OK: Converted to EPCIS 2.0 XML successfully.",
                          content =
                          @Content(
                                  schema = @Schema(implementation = EPCISDocument.class),
                                  examples = @ExampleObject(
                                          name = "EPCIS 2.0 XML document",
                                          ref = "xmlDocument",
                                          description = "Example EPCIS 2.0 document in XML format.")
                          )),
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
  public RestMulti<byte[]> testSomeNew(final InputStream body) throws IOException, ExecutionException, InterruptedException {
    xmlVersionTransformer.setExecutorService(managedExecutor);
    final InputStreamConsumingPublisher publisher = new InputStreamConsumingPublisher();
    return RestMulti.fromMultiData(Multi.createFrom().<byte[]>emitter(em-> {
      managedExecutor.execute(() -> {
        try {
          publisher.doBlockingWrite(
                  versionTransformer
                          .convert(
                                  body,
                                  Conversion.builder()
                                          .fromMediaType(EPCISFormat.XML)
                                          .fromVersion(EPCISVersion.VERSION_1_2_0)
                                          .toMediaType(EPCISFormat.XML)
                                          .toVersion(EPCISVersion.VERSION_2_0_0)
                                          .build())
          );
        } catch (IOException e) {
          em.fail(e);
          throw new RuntimeException(e);
        }
      });
      Multi.createFrom().publisher(FlowAdapters.toFlowPublisher(publisher))
              .runSubscriptionOn(managedExecutor)
              .subscribe().with(b -> em.emit(b.array()), em::fail, em::complete);
    })).build();
  }

}
