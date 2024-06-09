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
