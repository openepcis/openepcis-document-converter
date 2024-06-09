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
