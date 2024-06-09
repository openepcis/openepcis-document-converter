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
package io.openepcis.converter.servlet.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openepcis.model.rest.ProblemResponseBody;
import io.quarkus.logging.Log;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

public class CDITestProducer {


    @Produces
    Marshaller marshaller() throws JAXBException {
        try {
            final JAXBContext ctx =
                    JAXBContext.newInstance(ProblemResponseBody.class);
            Log.info("created Validation Test JAXBContext : " + ctx.getClass().getName());
            return ctx.createMarshaller();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Produces
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
