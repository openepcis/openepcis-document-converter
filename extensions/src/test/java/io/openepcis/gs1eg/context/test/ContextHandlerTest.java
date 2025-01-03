package io.openepcis.gs1eg.context.test;

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.Conversion;
import io.openepcis.converter.VersionTransformer;
import io.openepcis.converter.common.GS1FormatSupport;
import io.openepcis.converter.util.XMLFormatter;
import io.openepcis.model.epcis.format.FormatPreference;
import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContextHandlerTest {

    final XMLFormatter formatter = new XMLFormatter();
    final VersionTransformer versionTransformer;

    public ContextHandlerTest() throws JAXBException {
        versionTransformer = new VersionTransformer();
    }

    @Test
    void jsonToXmlTest() throws Exception {
        final InputStream inputStream = getClass().getResourceAsStream("/1. GS1_Eg_EPCIS_Document.json");
        final Conversion conversion = Conversion.builder()
                .fromMediaType(EPCISFormat.JSON_LD)
                .fromVersion(EPCISVersion.VERSION_2_0_0)
                .toMediaType(EPCISFormat.XML)
                .toVersion(EPCISVersion.VERSION_2_0_0).build();

        final OutputStream outputStream = new ByteArrayOutputStream();
        versionTransformer.convert(inputStream, conversion).transferTo(outputStream);
        assertTrue(outputStream.toString().length() > 0);
        System.out.println(formatter.format(outputStream.toString()));
    }

    @Test
    void xmlToJsonTest() throws Exception {
        final InputStream inputStream = getClass().getResourceAsStream("/1. GS1_Eg_EPCIS_Document.xml");
        final Conversion conversion = Conversion.builder()
                .fromMediaType(EPCISFormat.XML)
                .fromVersion(EPCISVersion.VERSION_2_0_0)
                .toMediaType(EPCISFormat.JSON_LD)
                .toVersion(EPCISVersion.VERSION_2_0_0).build();

        final OutputStream outputStream = new ByteArrayOutputStream();
        versionTransformer.convert(inputStream, conversion).transferTo(outputStream);
        assertTrue(outputStream.toString().length() > 0);
        System.out.println(outputStream);
    }

    @Test
    void jsonToXmlWithMapperTest() throws Exception {
        final InputStream inputStream = getClass().getResourceAsStream("/2. GS1_Eg_EPCIS_Document.json");
        final FormatPreference withWebURI = new FormatPreference("always_gs1_digital_link", "always_web_uri");
        final BiFunction<Object, List<Object>, Object> mapper = GS1FormatSupport.createMapper(withWebURI);
        final Conversion conversion = Conversion.builder()
                .fromMediaType(EPCISFormat.JSON_LD)
                .fromVersion(EPCISVersion.VERSION_2_0_0)
                .toMediaType(EPCISFormat.XML)
                .toVersion(EPCISVersion.VERSION_2_0_0).build();

        final OutputStream outputStream = new ByteArrayOutputStream();
        versionTransformer.mapWith(mapper).convert(inputStream, conversion).transferTo(outputStream);
        assertTrue(outputStream.toString().length() > 0);
        System.out.println(formatter.format(outputStream.toString()));
    }

    @Test
    void xmlToJsonWithMapperTest() throws Exception {
        final InputStream inputStream = getClass().getResourceAsStream("/2. GS1_Eg_EPCIS_Document.xml");
        final FormatPreference withWebURI = new FormatPreference("always_gs1_digital_link", "always_web_uri");
        final BiFunction<Object, List<Object>, Object> mapper = GS1FormatSupport.createMapper(withWebURI);

        final Conversion conversion = Conversion.builder()
                .fromMediaType(EPCISFormat.XML)
                .fromVersion(EPCISVersion.VERSION_2_0_0)
                .toMediaType(EPCISFormat.JSON_LD)
                .toVersion(EPCISVersion.VERSION_2_0_0).build();

        final OutputStream outputStream = new ByteArrayOutputStream();
        versionTransformer.mapWith(mapper).convert(inputStream, conversion).transferTo(outputStream);
        assertTrue(outputStream.toString().length() > 0);
        System.out.println(outputStream);
    }
}
