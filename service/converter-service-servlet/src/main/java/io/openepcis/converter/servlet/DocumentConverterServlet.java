package io.openepcis.converter.servlet;

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.Conversion;
import io.openepcis.converter.VersionTransformer;
import io.openepcis.converter.common.GS1FormatSupport;
import io.openepcis.model.epcis.format.FormatPreference;
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
import java.util.List;
import java.util.Optional;

@WebServlet(name = "DocumentConverterServlet", urlPatterns = "/api/convert/*")
public class DocumentConverterServlet extends HttpServlet {

    private static final List<String> PRODUCES = List.of(
            MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML,
            "application/problem+json",
            MediaType.WILDCARD);

    private static final List<String> CONSUMES = List.of(
            MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML,
            "application/ld+json");

    private static final String CONVERT_JSON_2_0 = "/convert/json/2.0";

    private static final String CONVERT_XML_2_0 = "/convert/xml/2.0";

    private static final String CONVERT_XML_1_2 = "/convert/xml/1.2";

    @Inject
    VersionTransformer versionTransformer;

    @Inject
    ServletSupport servletSupport;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final Optional<String> accept = servletSupport.accept(PRODUCES, req, resp);
        if (accept.isEmpty()) {
            return;
        }
        final Optional<String> contentType =servletSupport.contentType(CONSUMES, accept.get(), req, resp);
        if (contentType.isEmpty()) {
            return;
        }
        final Optional<EPCISFormat> from = EPCISFormat.fromString(contentType.get());
        if (from.isEmpty()) {
            servletSupport.writeException(new BadRequestException("unable to detect EPCISFormat"), accept.get(), resp);
            return;
        }
        try {
            if (req.getRequestURI().endsWith(CONVERT_JSON_2_0)) {
                if (!servletSupport.accepted(accept, "json", resp)) {
                    return;
                }
                resp.setContentType(MediaType.APPLICATION_JSON + ";charset=UTF-8");
                runVersionTransformer(req, resp, from.get(), EPCISFormat.JSON_LD, EPCISVersion.VERSION_2_0_0);
            } else {
                if (!servletSupport.accepted(accept, "xml", resp)) {
                    return;
                }
                resp.setContentType(MediaType.APPLICATION_XML + ";charset=UTF-8");
                if (req.getRequestURI().endsWith(CONVERT_XML_2_0)) {
                    runVersionTransformer(req, resp, from.get(), EPCISFormat.XML, EPCISVersion.VERSION_2_0_0);
                } else if (req.getRequestURI().endsWith(CONVERT_XML_1_2)) {
                    runVersionTransformer(req, resp, from.get(), EPCISFormat.XML, EPCISVersion.VERSION_1_2_0);
                } else {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                }
            }
        } catch (Exception e) {
            servletSupport.writeException(new BadRequestException(e.getMessage(), e), accept.get(), resp);
        }
    }

    private void runVersionTransformer(HttpServletRequest req, HttpServletResponse resp, final EPCISFormat from, final EPCISFormat to, final EPCISVersion version) throws IOException {
        versionTransformer
                .mapWith(GS1FormatSupport.createMapper(getFormatPreference(req)))
                .convert(
                    req.getInputStream(),
                    Conversion.builder().fromMediaType(from)
                        .toMediaType(to)
                        .toVersion(version)
                        .build()
                )
            .transferTo(resp.getOutputStream());
    }

    private FormatPreference getFormatPreference(HttpServletRequest req) {
        return GS1FormatSupport.getFormatPreference(GS1FormatSupport.createRequestFacade(req::getHeader));
    }

}
