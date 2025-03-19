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
package io.openepcis.converter.servlet;

import io.openepcis.converter.common.IdentifierConverterUtil;
import io.openepcis.model.rest.servlet.ServletSupport;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.io.IOUtils;

public class IdentifierConverterServlets {

  @WebServlet(
      name = "IdentifierConverterServlets.ConvertToWebURI",
      urlPatterns = "/api/convert/identifier/web-uri")
  public static final class ConvertToWebURI extends HttpServlet {

    @Inject ServletSupport servletSupport;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      final Optional<String> accept =
          servletSupport.accept(List.of(MediaType.APPLICATION_JSON, MediaType.WILDCARD), req, resp);
      if (!servletSupport.accepted(accept, MediaType.APPLICATION_JSON, resp)) {
        return;
      }
      if (servletSupport
          .contentType(List.of(MediaType.TEXT_PLAIN), accept.get(), req, resp)
          .isEmpty()) {
        return;
      }
      try {
        final String result =
            IdentifierConverterUtil.toWebURI(
                IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8));
        resp.setContentType(MediaType.TEXT_PLAIN);
        resp.getWriter().write(result);
      } catch (Exception e) {
        servletSupport.writeException(
            new BadRequestException(e.getMessage(), e), MediaType.APPLICATION_JSON, resp);
      }
    }
  }

  @WebServlet(
      name = "IdentifierConverterServlets.ConvertToURN",
      urlPatterns = "/api/convert/identifier/urn")
  public static final class ConvertToURN extends HttpServlet {

    @Inject ServletSupport servletSupport;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      final Optional<String> accept =
          servletSupport.accept(List.of(MediaType.APPLICATION_JSON, MediaType.WILDCARD), req, resp);
      if (!servletSupport.accepted(accept, MediaType.TEXT_PLAIN, resp)) {
        return;
      }
      if (servletSupport
          .contentType(List.of(MediaType.TEXT_PLAIN), accept.get(), req, resp)
          .isEmpty()) {
        return;
      }
      try {
        final Map<String, String> result =
            IdentifierConverterUtil.toURN(
                IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8));
        resp.setContentType(MediaType.APPLICATION_JSON);
        servletSupport.writeJson(resp, result);
      } catch (Exception e) {
        servletSupport.writeException(
            new BadRequestException(e.getMessage(), e), MediaType.APPLICATION_JSON, resp);
      }
    }
  }
}
