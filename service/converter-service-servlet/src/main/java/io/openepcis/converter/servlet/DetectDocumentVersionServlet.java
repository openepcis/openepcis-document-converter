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
    @Inject
    VersionTransformer versionTransformer;

    @Inject
    ServletSupport servletSupport;

    private static final List<String> PRODUCES = List.of(
            MediaType.APPLICATION_JSON,
            "application/problem+json",
            MediaType.WILDCARD);

    private static final List<String> CONSUMES = List.of(
            MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML,
            "application/ld+json");

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final Optional<String> accept = servletSupport.accept(PRODUCES, req, resp);
        if (accept.isEmpty()) {
            return;
        }
        final Optional<String> contentType = servletSupport.contentType(CONSUMES, accept.get(), req, resp);
        if (contentType.isEmpty()) {
            return;
        }
        try {
            final String version = versionTransformer.versionDetector(new BufferedInputStream(req.getInputStream(), 8192)).getVersion();
            final Map<String, Object> response = new HashMap<>() {{
                put("version", version);
            }};
            resp.setContentType(MediaType.APPLICATION_JSON + ";charset=UTF-8");
            servletSupport.writeJson(resp, response);
        } catch (Exception e) {
            servletSupport.writeException(new BadRequestException(e.getMessage(), e), accept.get(), resp);
        }
    }

}
