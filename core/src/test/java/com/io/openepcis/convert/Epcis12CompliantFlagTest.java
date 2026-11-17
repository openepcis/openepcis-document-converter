/*
 * Copyright 2022-2026 benelog GmbH & Co. KG
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
package com.io.openepcis.convert;

import io.openepcis.constants.EPCISFormat;
import io.openepcis.constants.EPCISVersion;
import io.openepcis.converter.Conversion;
import io.openepcis.converter.VersionTransformer;
import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

// Library level: the flag true keeps the 2.0 only content as extensions, the REST header GS1-EPCIS-1.2-Compliant reads the opposite way
class Epcis12CompliantFlagTest {

    private static final String XML_DOCUMENT = "2.0/EPCIS/XML/Capture/Documents/Combination_of_different_event.xml";
    private static final String JSON_DOCUMENT = "2.0/EPCIS/JSON/Capture/Documents/Combination_of_different_event.json";
    private static final String[] TWO_DOT_ZERO_ONLY = {"AssociationEvent", "sensorElementList", "persistentDisposition"};

    private final VersionTransformer versionTransformer;

    Epcis12CompliantFlagTest() throws JAXBException {
        this.versionTransformer = new VersionTransformer();
    }

    @ParameterizedTest(name = "XML 2.0 to XML 1.2, flag {0}, 2.0 only content kept: {1}")
    @CsvSource(value = {"null,true", "true,true", "false,false"}, nullValues = "null")
    void xmlToXml12(final Boolean flag, final boolean kept) throws Exception {
        assertContent(convert(XML_DOCUMENT, EPCISFormat.XML, flag), kept);
    }

    @ParameterizedTest(name = "JSON 2.0 to XML 1.2, flag {0}, 2.0 only content kept: {1}")
    @CsvSource(value = {"null,true", "true,true", "false,false"}, nullValues = "null")
    void jsonToXml12(final Boolean flag, final boolean kept) throws Exception {
        assertContent(convert(JSON_DOCUMENT, EPCISFormat.JSON_LD, flag), kept);
    }

    private static void assertContent(final String xml, final boolean kept) {
        for (final String element : TWO_DOT_ZERO_ONLY) {
            assertEquals(kept, xml.contains(element), element + " in the 1.2 output");
        }
        assertTrue(xml.contains("ObjectEvent"), "the standard 1.2 events must always be there");
    }

    private String convert(final String resource, final EPCISFormat from, final Boolean flag) throws Exception {
        Conversion.StartStage stage = Conversion.builder();
        if (flag != null) {
            stage = stage.generateGS1CompliantDocument(flag);
        }
        final Conversion conversion = stage
                .fromMediaType(from)
                .toMediaType(EPCISFormat.XML)
                .toVersion(EPCISVersion.VERSION_1_2_0)
                .build();

        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "document missing from the classpath: " + resource);
            try (InputStream out = versionTransformer.convert(in, conversion)) {
                return new String(out.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
