package io.openepcis.convert.xml;

import io.openepcis.model.rest.ProblemResponseBody;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProblemResponseBodyMarshaller {

    private static Marshaller marshaller;

    static {
        try {
            JAXBContext context = JAXBContext.newInstance(ProblemResponseBody.class);
            marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        } catch (JAXBException e) {
            log.error(e.getMessage(), e);
        }
    }

    public static Marshaller getMarshaller() {
        return marshaller;
    }

    public static void setMarshaller(Marshaller marshaller) {
        ProblemResponseBodyMarshaller.marshaller = marshaller;
    }
}
