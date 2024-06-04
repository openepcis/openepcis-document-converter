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
