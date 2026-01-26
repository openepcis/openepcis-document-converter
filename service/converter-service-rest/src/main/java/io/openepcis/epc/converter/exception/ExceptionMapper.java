/*
 * Copyright 2022-2024 benelog GmbH & Co. KG
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

    // Build detailed message including root cause for better error diagnostics
    final String rootCauseMsg = getRootCauseMessage(exception);
    String detailMessage = exception.getMessage();
    if (!rootCauseMsg.equals(exception.getMessage())) {
      detailMessage = detailMessage + " [Root cause: " + rootCauseMsg + "]";
    }

    ProblemResponseBody responseBody = new ProblemResponseBody()
        .type(exception.getClass().getSimpleName())
        .title("Bad Request")
        .status(RestResponse.Status.BAD_REQUEST.getStatusCode())
        .detail(detailMessage);
    return RestResponse.status(RestResponse.Status.BAD_REQUEST, responseBody);
  }

  /**
   * Extracts the root cause message from an exception chain.
   *
   * @param e the exception to extract the root cause from
   * @return the root cause message, or the original exception message if no root cause found
   */
  private String getRootCauseMessage(Throwable e) {
    Throwable root = e;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    return root.getMessage() != null ? root.getMessage() : e.getMessage();
  }
}
