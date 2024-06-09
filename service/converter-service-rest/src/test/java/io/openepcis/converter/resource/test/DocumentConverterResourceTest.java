package io.openepcis.converter.resource.test;

import io.openepcis.converter.service.restassured.AbstractDocumentConverterTest;
import io.openepcis.epc.converter.resource.DocumentConverterResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

import java.net.URL;

@QuarkusTest
public class DocumentConverterResourceTest extends AbstractDocumentConverterTest {

    @TestHTTPEndpoint(DocumentConverterResource.class)
    @TestHTTPResource
    URL url;

    @Override
    public String url() {
        return url + "/convert";
    }

}
