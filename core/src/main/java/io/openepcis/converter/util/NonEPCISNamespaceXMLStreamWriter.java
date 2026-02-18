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
package io.openepcis.converter.util;

import io.openepcis.constants.EPCIS;
import io.openepcis.model.epcis.util.EPCISNamespacePrefixMapper;

import java.util.HashSet;
import java.util.Set;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public class NonEPCISNamespaceXMLStreamWriter extends DelegatingXMLStreamWriter {

    // Namespaces to skip at event level - includes EPCIS namespaces plus well-known ontology namespaces
    // that should only appear at document root level
    private static final Set<String> SKIP_NAMESPACE_URIS;

    static {
        Set<String> skipUris = new HashSet<>(EPCISNamespacePrefixMapper.EPCIS_NAMESPACE_MAP.keySet());
        // Add well-known namespaces that should be at document level only, not repeated at event level
        skipUris.add(EPCIS.RDFS_DOMAIN);   // http://www.w3.org/2000/01/rdf-schema#
        skipUris.add(EPCIS.OWL_DOMAIN);    // http://www.w3.org/2002/07/owl#
        skipUris.add(EPCIS.XML_SCHEMA_INSTANCE); // http://www.w3.org/2001/XMLSchema-instance
        skipUris.add(EPCIS.XSD_DOMAIN);    // "http://www.w3.org/2001/XMLSchema#"
        skipUris.add(EPCIS.XSD_DOMAIN2);   //"http://www.w3.org/2001/XMLSchema"
        // Note: CBV_MDA_URN must NOT be skipped - it's needed for ILMD fields like cbvmda:countryOfExport
        SKIP_NAMESPACE_URIS = Set.copyOf(skipUris);
    }

    private final Set<String> documentNamespaceUris;

    public NonEPCISNamespaceXMLStreamWriter(XMLStreamWriter delegate) {
        super(delegate);
        this.documentNamespaceUris = Set.of();
    }

    public NonEPCISNamespaceXMLStreamWriter(XMLStreamWriter delegate, Set<String> documentNamespaceUris) {
        super(delegate);
        this.documentNamespaceUris = documentNamespaceUris != null ? Set.copyOf(documentNamespaceUris) : Set.of();
    }

    @Override
    public void writeNamespace(String prefix, String uri) throws XMLStreamException {
        if (!SKIP_NAMESPACE_URIS.contains(uri) && !documentNamespaceUris.contains(uri)) {
            delegate.writeNamespace(prefix, uri);
        }
    }
}
