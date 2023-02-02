package io.openepcis.convert.util;

import io.openepcis.model.epcis.util.EPCISNamespacePrefixMapper;
import java.util.Set;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public class NonEPCISNamespaceXMLStreamWriter extends DelegatingXMLStreamWriter {

  private static final Set<String> SKIP_NAMESPACE_URIS =
      EPCISNamespacePrefixMapper.EPCIS_NAMESPACE_MAP.keySet();

  public NonEPCISNamespaceXMLStreamWriter(XMLStreamWriter delegate) {
    super(delegate);
  }

  @Override
  public void writeNamespace(String prefix, String uri) throws XMLStreamException {
    if (!SKIP_NAMESPACE_URIS.contains(uri)) {
      delegate.writeNamespace(prefix, uri);
    }
  }
}
