package io.openepcis.epc.converter.util;

import io.openepcis.converter.common.GS1FormatSupport;
import io.openepcis.model.epcis.format.FormatPreference;
import io.vertx.core.http.HttpServerRequest;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class GS1FormatProvider {

  private final GS1FormatSupport.RequestHeaderFacade request;
  public GS1FormatProvider(final HttpServerRequest request) {
    this.request = GS1FormatSupport.createRequestFacade(request::getHeader);
  }

  public FormatPreference getFormatPreference() {
    return GS1FormatSupport.getFormatPreference(request);
  }

}
