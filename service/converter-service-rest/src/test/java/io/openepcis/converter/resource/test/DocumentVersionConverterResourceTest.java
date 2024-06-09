package io.openepcis.converter.resource.test;

import io.openepcis.converter.service.restassured.AbstractDocumentVersionConverterTest;
import io.openepcis.epc.converter.resource.DocumentVersionConverterResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

import java.net.URL;

@QuarkusTest
public class DocumentVersionConverterResourceTest extends AbstractDocumentVersionConverterTest {

  @TestHTTPEndpoint(DocumentVersionConverterResource.class)
  @TestHTTPResource
  URL url;

  @Override
  public String url() {
    return url + "/convert/version";
  }

}
