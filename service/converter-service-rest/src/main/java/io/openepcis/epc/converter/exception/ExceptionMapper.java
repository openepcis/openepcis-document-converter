package io.openepcis.epc.converter.exception;

import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.model.rest.ProblemResponseBody;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@Slf4j
public class ExceptionMapper {

  @ServerExceptionMapper
  public final RestResponse<ProblemResponseBody> mapException(final FormatConverterException exception) {
    log.error(exception.getMessage(), exception);
    ProblemResponseBody responseBody = ProblemResponseBody.fromException(exception, RestResponse.Status.BAD_REQUEST);
    return RestResponse.status(RestResponse.Status.BAD_REQUEST, responseBody);
  }

}