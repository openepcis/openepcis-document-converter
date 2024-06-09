package io.openepcis.converter.resource.test;

import io.openepcis.converter.service.restassured.AbstractIdentifierConverterTest;
import io.openepcis.epc.converter.resource.IdentifierConverterResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

import java.net.URL;

@QuarkusTest
public class IdentifierConverterResourceTest extends AbstractIdentifierConverterTest {

  @TestHTTPEndpoint(IdentifierConverterResource.class)
  @TestHTTPResource
  URL url;

  @Override
  public String webURIUrl() {
    return url + "/convert/identifier/web-uri";
  }

  @Override
  public String urnUrl() {
    return url + "/convert/identifier/urn";
  }

}
