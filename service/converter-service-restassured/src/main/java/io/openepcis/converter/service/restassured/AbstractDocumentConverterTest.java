package io.openepcis.converter.service.restassured;

import io.openepcis.resources.util.Commons;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

@QuarkusTest
public abstract class AbstractDocumentConverterTest {

    private static String jsonConverterAPI_2_0;
    private static String xmlConverterAPI_2_0;
    private static String xmlConverterAPI_1_2;

    public abstract String url();

    @BeforeEach
    public void testApiEndpoint() {
        jsonConverterAPI_2_0 = url() + "/json/2.0";
        xmlConverterAPI_2_0 = url() + "/xml/2.0";
        xmlConverterAPI_1_2 = url() + "/xml/1.2";
    }

    /**
     * REST Assured test cases for converting the XML to JSON/JSON-LD
     */

    // Invalid input content type
    @Test
    public void jsonConverterInvalidContentTypeTest() {
        final Response response =
                RestAssured.given()
                        .contentType(ContentType.TEXT)
                        .body(Commons.getInputStream("2.0/EPCIS/XML/Capture/Documents/AggregationEvent.xml"))
                        .when()
                        .post(jsonConverterAPI_2_0);

        Assertions.assertEquals(415, response.getStatusCode());
        Assertions.assertEquals("NotSupportedException", response.jsonPath().get("type"));
        Assertions.assertEquals(
                "The content-type header value did not match the value in @Consumes",
                response.jsonPath().get("detail"));
    }

    // Invalid request body value
    @Test
    public void jsonConverterInvalidRequestBodyTest() {
        final Response response =
                RestAssured.given()
                        .contentType(ContentType.XML)
                        .body(Commons.getInputStream("2.0/EPCIS/JSON/Capture/Documents/AggregationEvent.json"))
                        .when()
                        .post(jsonConverterAPI_2_0);
        Assertions.assertEquals(400, response.getStatusCode());
    }

    // Empty request body
    @Test
    public void jsonConverterEmptyRequestBodyTest() {
        // Use assertThrows to catch and verify the expected exception
        IllegalArgumentException exception =
                Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                RestAssured.given()
                                        .contentType(ContentType.XML)
                                        .body(
                                                Commons.getInputStream(
                                                        "2.0/EPCIS/XML/Capture/Documents/AggregationEvent1.xml"))
                                        .when()
                                        .post(jsonConverterAPI_2_0));

        // Verify the error message of the caught exception
        Assertions.assertEquals("body cannot be null", exception.getMessage());
    }

    // Valid request for converting to JSON/JSON-LD
    @Test
    public void jsonConverterValidRequestTest() throws IOException {
        final Response response =
                RestAssured.given()
                        .contentType(ContentType.XML)
                        .body(
                                IOUtils.toString(
                                        Commons.getInputStream("2.0/EPCIS/XML/Capture/Documents/ObjectEvent.xml"),
                                        StandardCharsets.UTF_8))
                        .when()
                        .post(jsonConverterAPI_2_0);
        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals("application/json;charset=UTF-8", response.getContentType());
        Assertions.assertEquals(2, (int) response.jsonPath().get("epcisBody.eventList.size()"));
    }

    // Valid request for converting to JSON/JSON-LD
    @Test
    public void jsonConverterValidRequest2Test() throws IOException {
        final Response response =
                RestAssured.given()
                        .contentType(ContentType.XML)
                        .body(
                                IOUtils.toString(
                                        Commons.getInputStream(
                                                "2.0/EPCIS/XML/Capture/Documents/Combination_of_different_event.xml"),
                                        StandardCharsets.UTF_8))
                        .when()
                        .post(jsonConverterAPI_2_0);
        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals("application/json;charset=UTF-8", response.getContentType());
        Assertions.assertEquals(6, (int) response.jsonPath().get("epcisBody.eventList.size()"));
    }

    /**
     * REST Assured test cases for converting JSON/JSON-LD to 2.0 XML
     */

    // Invalid input content type
    @Test
    public void xmlConverterInvalidContentTypeTest() {
        final Response response =
                RestAssured.given()
                        .contentType(ContentType.TEXT)
                        .body(Commons.getInputStream("2.0/EPCIS/JSON/Capture/Documents/AggregationEvent.json"))
                        .when()
                        .post(xmlConverterAPI_2_0);

        Assertions.assertEquals(415, response.getStatusCode());
        Assertions.assertEquals("NotSupportedException", response.jsonPath().get("type"));
        Assertions.assertEquals(
                "The content-type header value did not match the value in @Consumes",
                response.jsonPath().get("detail"));
    }

    // Empty request body
    @Test
    public void xmlConverterEmptyRequestBodyTest() {
        // Use assertThrows to catch and verify the expected exception
        IllegalArgumentException exception =
                Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                RestAssured.given()
                                        .contentType(ContentType.JSON)
                                        .body(
                                                Commons.getInputStream(
                                                        "2.0/EPCIS/JSON/Capture/Documents/AggregationEvent1.json"))
                                        .when()
                                        .post(xmlConverterAPI_2_0));

        // Verify the error message of the caught exception
        Assertions.assertEquals("body cannot be null", exception.getMessage());
    }

    // Valid request for converting JSON to XML 2.0
    @Test
    public void xmlConverterValidRequestTest() {
        final Response response =
                RestAssured.given()
                        .contentType(ContentType.JSON)
                        .body(Commons.getInputStream("2.0/EPCIS/JSON/Capture/Documents/AggregationEvent.json"))
                        .when()
                        .post(xmlConverterAPI_2_0);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals("application/xml;charset=UTF-8", response.getContentType());
        final String responseBody = response.getBody().asString();
        Assertions.assertEquals(1, Commons.getXmlPath(responseBody).getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".AggregationEvent").size());
    }

    // Valid request for converting JSON to XML 2.0
    @Test
    public void xmlConverterValidRequest2Test() {
        final Response response =
                RestAssured.given()
                        .contentType(ContentType.JSON)
                        .body(
                                Commons.getInputStream(
                                        "2.0/EPCIS/JSON/Capture/Documents/Namespaces_at_different_level.json"))
                        .when()
                        .post(xmlConverterAPI_2_0);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals("application/xml;charset=UTF-8", response.getContentType());
        final String responseBody = response.getBody().asString();
        Assertions.assertEquals(5, Commons.getXmlPath(responseBody).getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".ObjectEvent").size());
    }

    // Valid request for converting JSON to XML 2.0
    @Test
    public void xmlConverterValidRequest3Test() {
        final Response response =
                RestAssured.given()
                        .contentType(ContentType.JSON)
                        .body(
                                Commons.getInputStream(
                                        "2.0/EPCIS/JSON/Capture/Documents/Combination_of_different_event.json"))
                        .when()
                        .post(xmlConverterAPI_2_0);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals("application/xml;charset=UTF-8", response.getContentType());
        final String responseBody = response.getBody().asString();
        Assertions.assertEquals(1, Commons.getXmlPath(responseBody).getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".ObjectEvent").size());
        Assertions.assertEquals(1, Commons.getXmlPath(responseBody).getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".TransformationEvent").size());
        Assertions.assertEquals(2, Commons.getXmlPath(responseBody).getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".AssociationEvent").size());
    }

    /**
     * REST Assured test cases for converting XML to 2.0 XML
     */

    @Test
    public void xmlConverterValidRequest4Test() throws Exception {
        final Response response =
                RestAssured.given()
                        .contentType(ContentType.XML)
                        .body(IOUtils.toString(Commons.getInputStream("2.0/EPCIS/XML/Capture/Documents/AssociationEvent.xml"), StandardCharsets.UTF_8))
                        .when()
                        .post(xmlConverterAPI_2_0);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals("application/xml;charset=UTF-8", response.getContentType());
        final String responseBody = response.getBody().asString();
        Assertions.assertEquals(
                2,
                Commons.getXmlPath(responseBody)
                        .getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".AssociationEvent")
                        .size());
    }

    @Test
    public void xmlConverterValidRequest5Test() throws Exception {
        final Response response =
                RestAssured.given()
                        .contentType(ContentType.XML)
                        .body(IOUtils.toString(Commons.getInputStream("1.2/EPCIS/XML/Capture/Documents/ObjectEvent.xml"), StandardCharsets.UTF_8))
                        .when()
                        .post(xmlConverterAPI_2_0);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals("application/xml;charset=UTF-8", response.getContentType());
        final String responseBody = response.getBody().asString();
        Assertions.assertEquals(
                1,
                Commons.getXmlPath(responseBody)
                        .getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".ObjectEvent")
                        .size());
    }
    /**
     * REST Assured test cases for converting JSON/JSON-LD to 1.2 XML
     */

    // Invalid input content type
    @Test
    public void xmlConverter_1_2_InvalidContentTypeTest() {
        final Response response =
                RestAssured.given()
                        .contentType(ContentType.TEXT)
                        .body(Commons.getInputStream("2.0/EPCIS/JSON/Capture/Documents/AggregationEvent.json"))
                        .when()
                        .post(xmlConverterAPI_1_2);

        Assertions.assertEquals(415, response.getStatusCode());
        Assertions.assertEquals("NotSupportedException", response.jsonPath().get("type"));
        Assertions.assertEquals(
                "The content-type header value did not match the value in @Consumes",
                response.jsonPath().get("detail"));
    }

    // Empty request body
    @Test
    public void xmlConverter_1_2_EmptyRequestBodyTest() {
        // Use assertThrows to catch and verify the expected exception
        IllegalArgumentException exception =
                Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                RestAssured.given()
                                        .contentType(ContentType.JSON)
                                        .body(
                                                Commons.getInputStream(
                                                        "2.0/EPCIS/JSON/Capture/Documents/AggregationEvent1.json"))
                                        .when()
                                        .post(xmlConverterAPI_2_0));

        // Verify the error message of the caught exception
        Assertions.assertEquals("body cannot be null", exception.getMessage());
    }

    // Valid request for converting JSON to XML 1.2
    @Test
    @Disabled
    public void xmlConverter_1_2_ValidRequestTest() {
        final Response response =
                RestAssured.given()
                        .contentType(ContentType.JSON)
                        .body(
                                Commons.getInputStream(
                                        "2.0/EPCIS/JSON/Capture/Documents/Combination_of_different_event.json"))
                        .when()
                        .post(xmlConverterAPI_1_2);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals("application/xml;charset=UTF-8", response.getContentType());
        final String responseBody = response.getBody().asString();
        Assertions.assertEquals(1, Commons.getXmlPath(responseBody).getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".ObjectEvent").size());
        Assertions.assertEquals(
                1,
                Commons.getXmlPath(responseBody).getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".extension.TransformationEvent").size());
        Assertions.assertEquals(
            0,
            Commons.getXmlPath(responseBody)
                        .getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".extension.extension.AssociationEvent")
                        .size());
    }

    @Test
    public void xmlConverter_1_2_ValidRequest2Test() {
        final Response response =
                RestAssured.given()
                        .contentType(ContentType.JSON)
                        .body(
                                Commons.getInputStream(
                                        "2.0/EPCIS/JSON/Capture/Documents/Namespaces_at_different_level.json"))
                        .when()
                        .post(xmlConverterAPI_1_2);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals("application/xml;charset=UTF-8", response.getContentType());
        final String responseBody = response.getBody().asString();
        Assertions.assertEquals(5, Commons.getXmlPath(responseBody).getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".ObjectEvent").size());
    }

    @Test
    @Disabled
    public void xmlConverter_1_2_ValidRequest3Test() {
        final Response response =
                RestAssured.given()
                        .contentType(ContentType.JSON)
                        .body(Commons.getInputStream("2.0/EPCIS/JSON/Capture/Documents/AssociationEvent.json"))
                        .when()
                        .post(xmlConverterAPI_1_2);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals("application/xml;charset=UTF-8", response.getContentType());
        final String responseBody = response.getBody().asString();
        Assertions.assertEquals(
            0,
            Commons.getXmlPath(responseBody)
                        .getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".extension.extension.AssociationEvent")
                        .size());
    }


    /**
     * REST Assured test cases for converting the XML to 1.2 XML
     */
    @Test
    @Disabled
    public void xmlConverter_1_2_ValidRequest4Test() throws Exception {
        final Response response =
                RestAssured.given()
                        .contentType(ContentType.XML)
                        .body(IOUtils.toString(Commons.getInputStream("2.0/EPCIS/XML/Capture/Documents/AssociationEvent.xml"), StandardCharsets.UTF_8))
                        .when()
                        .post(xmlConverterAPI_1_2);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals("application/xml;charset=UTF-8", response.getContentType());
        final String responseBody = response.getBody().asString();
        Assertions.assertEquals(
                0,
                Commons.getXmlPath(responseBody)
                        .getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".extension.extension.AssociationEvent")
                        .size());
    }

    @Test
    public void xmlConverter_1_2_ValidRequest5Test() throws Exception {
        final Response response =
                RestAssured.given()
                        .contentType(ContentType.XML)
                        .body(IOUtils.toString(Commons.getInputStream("1.2/EPCIS/XML/Capture/Documents/ObjectEvent.xml"), StandardCharsets.UTF_8))
                        .when()
                        .post(xmlConverterAPI_1_2);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals("application/xml;charset=UTF-8", response.getContentType());
        final String responseBody = response.getBody().asString();
        Assertions.assertEquals(
                1,
                Commons.getXmlPath(responseBody)
                        .getList(Commons.EPCIS_EPCISDOCUMENT_EPCISBODY_EVENT_LIST + ".ObjectEvent")
                        .size());
    }

    /**
     * Disabled Test for non-functional XML 1.2 -> JSON conversion with GS1-EPC-Format Always_GS1_Digital_Link
     * @throws Exception
     */
    @Test
    @Disabled
    public void xmlConverter_1_2_ValidRequestEPCFormatTest() throws Exception {
        final Response response =
                RestAssured.given()
                        .contentType(ContentType.XML)
                        .header("GS1-EPC-Format", "Always_GS1_Digital_Link")
                        .body(IOUtils.toString(Commons.getInputStream("1.2/EPCIS/XML/Capture/Documents/All_eventTypes_in_single_document.xml"), StandardCharsets.UTF_8))
                        .when()
                        .post(jsonConverterAPI_2_0);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals("application/json;charset=UTF-8", response.getContentType());
        Assertions.assertEquals(
            1,
            response.getBody().jsonPath()
                    .getList("epcisBody.eventList", HashMap.class)
                    .stream()
                    .filter(eventAsMap -> eventAsMap.get("type")
                            .equals("ObjectEvent"))
                    .count()
        );
    }

}
