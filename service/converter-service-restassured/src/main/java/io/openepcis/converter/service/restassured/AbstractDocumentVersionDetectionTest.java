package io.openepcis.converter.service.restassured;

import io.openepcis.resources.util.Commons;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

@QuarkusTest
public abstract class AbstractDocumentVersionDetectionTest {

  private String documentVersionDetectionAPI;

  public abstract String url();

  @BeforeEach
  public void testApiEndpoint() {
    documentVersionDetectionAPI = url() + "/document/version";
  }

  @Test
  public void versionDetectionTest_XML_1_2_0() throws Exception {
    final Response response =
        RestAssured.given()
            .contentType(ContentType.XML)
            .body(IOUtils.toString(Commons.getInputStream("1.2/EPCIS/XML/Capture/Documents/ObjectEvent.xml"), StandardCharsets.UTF_8))
            .when()
            .post(documentVersionDetectionAPI);

    Assertions.assertEquals(200, response.getStatusCode());
    Assertions.assertEquals("1.2.0", response.jsonPath().get("version"));
  }

  @Test
  public void versionDetectionTest_JSON_2_0_0() throws Exception {
    final Response response =
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(IOUtils.toString(Commons.getInputStream("2.0/EPCIS/JSON/Capture/Documents/ObjectEvent.json"), StandardCharsets.UTF_8))
            .when()
            .post(documentVersionDetectionAPI);

    Assertions.assertEquals(200, response.getStatusCode());
    Assertions.assertEquals("2.0.0", response.jsonPath().get("version"));
  }

  @Test
  public void versionDetectionTest_XML_2_0_0() throws Exception {
    final Response response =
        RestAssured.given()
            .contentType(ContentType.XML)
            .body(IOUtils.toString(Commons.getInputStream("2.0/EPCIS/XML/Capture/Documents/ObjectEvent.xml"), StandardCharsets.UTF_8))
            .when()
            .post(documentVersionDetectionAPI);

    Assertions.assertEquals(200, response.getStatusCode());
    Assertions.assertEquals("2.0.0", response.jsonPath().get("version"));
  }
}
