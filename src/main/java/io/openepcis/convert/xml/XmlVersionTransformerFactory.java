package io.openepcis.convert.xml;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;

public interface XmlVersionTransformerFactory {

    public XmlVersionTransformer newXmlVersionTransformer(final ExecutorService executorService);

}
