package io.openepcis.converter.servlet.test;

import io.openepcis.converter.service.restassured.AbstractDocumentConverterTest;
import io.openepcis.converter.servlet.DocumentConverterServlet;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

import java.net.URL;

@QuarkusTest
public class DocumentConverterServletTest extends AbstractDocumentConverterTest {


    @TestHTTPEndpoint(DocumentConverterServlet.class)
    @TestHTTPResource
    URL url;

    @Override
    public String url() {
        // need to remove trailing slash being introduced by wildcard url pattern
        return url.toString().substring(0, url.toString().length()-1);
    }

}
