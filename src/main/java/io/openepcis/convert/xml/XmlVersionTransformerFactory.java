package io.openepcis.convert.xml;

import java.util.concurrent.ExecutorService;

public class XmlVersionTransformerFactory {

    public static final XmlVersionTransformer newXmlVersionTransformer(final ExecutorService executorService) {
        return new DefaultXmlVersionTransformer(executorService);
    }
}
