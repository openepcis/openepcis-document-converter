package io.openepcis.converter.resource.test;

import io.openepcis.converter.service.restassured.AbstractDocumentVersionDetectionTest;
import io.openepcis.epc.converter.resource.DocumentConverterResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

import java.net.URL;

@QuarkusTest
public class DocumentVersionDetectionTest extends AbstractDocumentVersionDetectionTest {

  @TestHTTPEndpoint(DocumentConverterResource.class)
  @TestHTTPResource
  URL url;

  @Override
  public String url() {
    return url.toString();
  }

}
