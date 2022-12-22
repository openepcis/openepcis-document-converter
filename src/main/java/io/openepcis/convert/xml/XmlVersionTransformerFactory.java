package io.openepcis.convert.xml;

import java.util.concurrent.ExecutorService;

public interface XmlVersionTransformerFactory {

  public XmlVersionTransformer newXmlVersionTransformer(final ExecutorService executorService);
}
