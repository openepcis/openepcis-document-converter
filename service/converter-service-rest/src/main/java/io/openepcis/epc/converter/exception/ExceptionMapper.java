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
    ProblemResponseBody responseBody = ProblemResponseBody.fromException(exception, RestResponse.Status.BAD_REQUEST);
    return RestResponse.status(RestResponse.Status.BAD_REQUEST, responseBody);
  }

}