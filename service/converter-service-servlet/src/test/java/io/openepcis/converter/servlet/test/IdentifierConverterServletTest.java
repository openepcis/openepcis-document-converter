package io.openepcis.converter.servlet.test;

import io.openepcis.converter.service.restassured.AbstractIdentifierConverterTest;
import io.openepcis.converter.servlet.IdentifierConverterServlets;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

import java.net.URL;

@QuarkusTest
public class IdentifierConverterServletTest extends AbstractIdentifierConverterTest {

  @TestHTTPEndpoint(IdentifierConverterServlets.ConvertToURN.class)
  @TestHTTPResource
  URL urnUrl;

  @TestHTTPEndpoint(IdentifierConverterServlets.ConvertToWebURI.class)
  @TestHTTPResource
  URL webURIUrl;
  @Override
  public String webURIUrl() {
    return webURIUrl.toString();
  }

  @Override
  public String urnUrl() {
    return urnUrl.toString();
  }

}
