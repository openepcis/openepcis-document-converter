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

import static org.junit.jupiter.api.Assertions.*;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractIdentifierConverterTest {

  private String webURIConvertAPI;
  private String urnConvertAPI;

  public abstract String webURIUrl();

  public abstract String urnUrl();

  @BeforeEach
  public void testApiEndpoint() {
    webURIConvertAPI = webURIUrl();
    urnConvertAPI = urnUrl();
  }

  /** REST Assured test cases for converting the URN to WebURI */

  // For valid data API returns status code of 200
  @Test
  public void convertToWebURIStatusCodeTest() {
    RestAssured.given()
        .body("urn:epc:id:sgtin:234567890.1123.9999")
        .when()
        .post(webURIConvertAPI)
        .then()
        .assertThat()
        .statusCode(200);
  }

  // For invalid data API returns status code of 400
  @Test
  public void convertToWebURIInvalidDataTest() {
    RestAssured.given()
        .body("urn:epc:id:sgtin:234567890.1123.")
        .when()
        .post(webURIConvertAPI)
        .then()
        .assertThat()
        .statusCode(400);
  }

  // Compare the returned value with correct value for Instance Identifier
  @Test
  public void convertToInstanceWebURITest() {
    final Response response =
        RestAssured.given()
            .body("urn:epc:id:sgtin:234567890.1123.9999")
            .when()
            .post(webURIConvertAPI);

    assertEquals(200, response.getStatusCode());
    assertEquals("https://id.gs1.org/01/12345678901231/21/9999", response.getBody().asString());
  }

  // Compare the returned value with correct value for Class Identifier
  @Test
  public void convertToClassWebURITest() {
    final Response response =
        RestAssured.given()
            .body("urn:epc:idpat:itip:483478.7347834.92.93.*")
            .when()
            .post(webURIConvertAPI);

    assertEquals(200, response.getStatusCode());
    assertEquals("https://id.gs1.org/8006/748347834783449293", response.getBody().asString());
  }

  // Invalid media type in request
  @Test
  public void convertToWebURIInvalidContentTypeTest() {
    RestAssured.given()
        .contentType(ContentType.JSON)
        .body("urn:epc:idpat:itip:483478.7347834.92.93.*")
        .when()
        .post(webURIConvertAPI)
        .then()
        .assertThat()
        .statusCode(415)
        .body("title", Matchers.equalTo("Unsupported Media Type"));
  }

  /** REST Assured test cases for converting the WebURI to URN */

  // For valid data API returns status code of 200
  @Test
  public void convertToURNStatusCodeTest() {
    RestAssured.given()
        .body("https://id.gs1.org/00/012345678901234567 6")
        .when()
        .post(urnConvertAPI)
        .then()
        .assertThat()
        .statusCode(200);
  }

  // For invalid data API returns status code of 400
  @Test
  public void convertToURNInvalidDataTest() {
    RestAssured.given()
        .body("https://id.gs1.org/00/01234567890123456A 10")
        .when()
        .post(urnConvertAPI)
        .then()
        .assertThat()
        .statusCode(400);
  }

  // Compare the returned value with correct value for Instance Identifier
  @Test
  public void convertToInstanceURNTest() {
    final Response response =
        RestAssured.given()
            .body("https://id.gs1.org/414/6880009384938 12")
            .when()
            .post(urnConvertAPI);

    assertEquals(200, response.getStatusCode());
    assertEquals("urn:epc:id:sgln:688000938493..0", response.jsonPath().getString("asURN"));
  }

  // Compare the returned value with correct value for Class Identifier
  @Test
  public void convertToClassURNTest() {
    final Response response =
        RestAssured.given()
            .body("https://id.gs1.org/01/49557283728732/10//8484892%")
            .when()
            .post(urnConvertAPI);

    assertEquals(200, response.getStatusCode());
    assertEquals(
        "urn:epc:class:lgtin:9557283.472873./8484892%", response.jsonPath().getString("asURN"));
  }

  // Invalid media type in request
  @Test
  public void convertToURNInvalidContentTypeTest() {
    RestAssured.given()
        .contentType(ContentType.JSON)
        .body("https://eclipse.org/401/12345678901234A")
        .when()
        .post(urnConvertAPI)
        .then()
        .assertThat()
        .statusCode(415)
        .body("title", Matchers.equalTo("Unsupported Media Type"));
  }
}
