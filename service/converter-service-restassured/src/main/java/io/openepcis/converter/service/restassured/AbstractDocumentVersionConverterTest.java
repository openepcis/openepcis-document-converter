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
package io.openepcis.converter.service.restassured;

import io.openepcis.resources.util.Commons;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public abstract class AbstractDocumentVersionConverterTest {


  private String xmlConverterAPI_2_0;
  private String xmlConverterAPI_1_2;

  public abstract String url();

  @BeforeEach
  public void testApiEndpoint() {
    xmlConverterAPI_2_0 = url() + "/2.0";
    xmlConverterAPI_1_2 = url() + "/1.2";
  }

  /** Convert the XML 2.0 document into XML 1.2 */

  // Invalid input content type
  @Test
  public void xml_1_2_ConverterInvalidContentTypeTest() throws IOException {
    final Response response =
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(
                IOUtils.toString(
                    Commons.getInputStream("2.0/EPCIS/XML/Capture/Documents/AggregationEvent.xml"),
                    StandardCharsets.UTF_8))
            .when()
            .post(xmlConverterAPI_1_2);
    assertEquals(415, response.getStatusCode());
    assertEquals("NotSupportedException", response.jsonPath().get("type"));
    assertEquals(
        "The content-type header value did not match the value in @Consumes",
        response.jsonPath().get("detail"));
  }

  // Valid request body value
  @Test
  public void xml_1_2_ConverterValidTest() throws IOException {
    final Response response =
        RestAssured.given()
            .contentType(ContentType.XML)
            .body(
                IOUtils.toString(
                    Commons.getInputStream("2.0/EPCIS/XML/Capture/Documents/AggregationEvent.xml"),
                    StandardCharsets.UTF_8))
            .when()
            .post(xmlConverterAPI_1_2);
    assertEquals(200, response.getStatusCode());
    assertEquals("application/xml;charset=UTF-8", response.getContentType());
    final String responseBody = response.getBody().asString();
    assertEquals(1, Commons.getXmlPath(responseBody).getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".AggregationEvent").size());
  }

  @Test
  @Disabled
  public void xml_1_2_ConverterValid2Test() throws IOException {
    final Response response =
        RestAssured.given()
            .contentType(ContentType.XML)
            .body(
                IOUtils.toString(
                    Commons.getInputStream("2.0/EPCIS/XML/Capture/Documents/AssociationEvent.xml"),
                    StandardCharsets.UTF_8))
            .when()
            .post(xmlConverterAPI_1_2);
    assertEquals(200, response.getStatusCode());
    assertEquals("application/xml;charset=UTF-8", response.getContentType());
    final String responseBody = response.getBody().asString();
    assertEquals(
        0,
        Commons.getXmlPath(responseBody)
            .getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".extension.extension.AssociationEvent")
            .size());
  }

  @Test
  public void xml_1_2_ConverterValid3Test() throws IOException {
    final Response response =
        RestAssured.given()
            .contentType(ContentType.XML)
            .body(
                IOUtils.toString(
                    Commons.getInputStream(
                        "2.0/EPCIS/XML/Capture/Documents/TransformationEvent.xml"),
                    StandardCharsets.UTF_8))
            .when()
            .post(xmlConverterAPI_1_2);
    assertEquals(200, response.getStatusCode());
    assertEquals("application/xml;charset=UTF-8", response.getContentType());
    final String responseBody = response.getBody().asString();
    assertEquals(
        1,
        Commons.getXmlPath(responseBody).getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".extension.TransformationEvent").size());
  }

  /** Convert the XML 1.2 document into XML 2.0 */
  // Invalid input content type
  @Test
  public void xml_2_0_ConverterInvalidContentTypeTest() throws IOException {
    final Response response =
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(
                IOUtils.toString(
                    Commons.getInputStream("1.2/EPCIS/XML/Capture/Documents/AggregationEvent.xml"),
                    StandardCharsets.UTF_8))
            .when()
            .post(xmlConverterAPI_2_0);
    assertEquals(415, response.getStatusCode());
    assertEquals("NotSupportedException", response.jsonPath().get("type"));
    assertEquals(
        "The content-type header value did not match the value in @Consumes",
        response.jsonPath().get("detail"));
  }

  // Valid request body value
  @Test
  public void xml_2_0_ConverterValidTest() throws IOException {
    final Response response =
        RestAssured.given()
            .contentType(ContentType.XML)
            .body(
                IOUtils.toString(
                    Commons.getInputStream("1.2/EPCIS/XML/Capture/Documents/AggregationEvent.xml"),
                    StandardCharsets.UTF_8))
            .when()
            .post(xmlConverterAPI_2_0);
    assertEquals(200, response.getStatusCode());
    assertEquals("application/xml;charset=UTF-8", response.getContentType());
    final String responseBody = response.getBody().asString();
    assertEquals(1, Commons.getXmlPath(responseBody).getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".AggregationEvent").size());
  }

  @Test
  public void xml_2_0_ConverterValid2Test() throws IOException {
    final Response response =
        RestAssured.given()
            .contentType(ContentType.XML)
            .body(
                IOUtils.toString(
                    Commons.getInputStream("1.2/EPCIS/XML/Capture/Documents/AssociationEvent.xml"),
                    StandardCharsets.UTF_8))
            .when()
            .post(xmlConverterAPI_2_0);
    assertEquals(200, response.getStatusCode());
    assertEquals("application/xml;charset=UTF-8", response.getContentType());
    final String responseBody = response.getBody().asString();
    assertEquals(1, Commons.getXmlPath(responseBody).getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".AssociationEvent").size());
  }

  @Test
  public void xml_2_0_ConverterValid3Test() throws IOException {
    final Response response =
        RestAssured.given()
            .contentType(ContentType.XML)
            .body(
                IOUtils.toString(
                    Commons.getInputStream(
                        "1.2/EPCIS/XML/Capture/Documents/TransformationEvent.xml"),
                    StandardCharsets.UTF_8))
            .when()
            .post(xmlConverterAPI_2_0);
    assertEquals(200, response.getStatusCode());
    assertEquals("application/xml;charset=UTF-8", response.getContentType());
    final String responseBody = response.getBody().asString();
    assertEquals(1, Commons.getXmlPath(responseBody).getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".TransformationEvent").size());
  }
}
