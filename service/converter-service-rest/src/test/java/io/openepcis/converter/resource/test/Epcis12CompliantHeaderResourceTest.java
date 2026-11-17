/*
 * Copyright 2022-2026 benelog GmbH & Co. KG
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
package io.openepcis.converter.resource.test;

import io.openepcis.epc.converter.resource.DocumentConverterResource;
import io.openepcis.resources.util.Commons;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Only an explicit false keeps the 2.0 only content, no header or any other value is strict EPCIS 1.2
@QuarkusTest
class Epcis12CompliantHeaderResourceTest {

    private static final String HEADER = "GS1-EPCIS-1.2-Compliant";
    private static final String XML_DOCUMENT = "2.0/EPCIS/XML/Capture/Documents/Combination_of_different_event.xml";
    private static final String JSON_DOCUMENT = "2.0/EPCIS/JSON/Capture/Documents/Combination_of_different_event.json";
    private static final String[] TWO_DOT_ZERO_ONLY = {"AssociationEvent", "sensorElementList", "persistentDisposition"};

    @TestHTTPEndpoint(DocumentConverterResource.class)
    @TestHTTPResource
    URL url;

    @ParameterizedTest(name = "{0} document, header [{1}], 2.0 only content kept: {2}")
    @CsvSource(
            value = {
                    "XML,null,false",
                    "XML,'',false",
                    "XML,true,false",
                    "XML,maybe,false",
                    "XML,1,false",
                    "XML,false,true",
                    "XML,FALSE,true",
                    "JSON,null,false",
                    "JSON,'',false",
                    "JSON,true,false",
                    "JSON,maybe,false",
                    "JSON,1,false",
                    "JSON,false,true",
                    "JSON,FALSE,true"
            },
            nullValues = "null")
    void headerControlsTwoDotZeroOnlyContent(final String kind, final String header, final boolean kept) throws Exception {
        final Response response = post(kind, header);

        assertEquals(200, response.getStatusCode());
        final String xml = response.getBody().asString();
        for (final String element : TWO_DOT_ZERO_ONLY) {
            assertEquals(kept, xml.contains(element), element + " in the 1.2 output");
        }
        assertTrue(xml.contains("ObjectEvent"), "the standard 1.2 events must always be there");
    }

    private Response post(final String kind, final String header) throws Exception {
        final boolean xml = "XML".equals(kind);
        final String body = new String(Commons.getInputStream(xml ? XML_DOCUMENT : JSON_DOCUMENT).readAllBytes(), StandardCharsets.UTF_8);
        RequestSpecification request = RestAssured.given().contentType(xml ? ContentType.XML : ContentType.JSON).body(body);

        if (header != null) {
            request = request.header(HEADER, header); // null means no header at all
        }

        return request.when().post(url + "/convert/xml/1.2");
    }
}
