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

import io.openepcis.converter.VersionTransformer;
import io.openepcis.model.rest.servlet.ServletSupport;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@WebServlet(name = "DetectDocumentVersionServlet", urlPatterns = "/api/document/version")
public class DetectDocumentVersionServlet extends HttpServlet {
  @Inject VersionTransformer versionTransformer;

  @Inject ServletSupport servletSupport;

  private static final List<String> PRODUCES =
      List.of(MediaType.APPLICATION_JSON, "application/problem+json", MediaType.WILDCARD);

  private static final List<String> CONSUMES =
      List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, "application/ld+json");

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    final Optional<String> accept = servletSupport.accept(PRODUCES, req, resp);
    if (accept.isEmpty()) {
      return;
    }
    final Optional<String> contentType =
        servletSupport.contentType(CONSUMES, accept.get(), req, resp);
    if (contentType.isEmpty()) {
      return;
    }
    try {
      final String version =
          versionTransformer
              .versionDetector(new BufferedInputStream(req.getInputStream(), 8192))
              .getVersion();
      final Map<String, Object> response =
          new HashMap<>() {
            {
              put("version", version);
            }
          };
      resp.setContentType(MediaType.APPLICATION_JSON + ";charset=UTF-8");
      servletSupport.writeJson(resp, response);
    } catch (Exception e) {
      servletSupport.writeException(new BadRequestException(e.getMessage(), e), accept.get(), resp);
    }
  }
}
