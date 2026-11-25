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
package io.openepcis.converter.util;

import io.openepcis.constants.EPCIS;
import io.openepcis.model.epcis.util.EPCISNamespacePrefixMapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Writes an event fragment without repeating the namespace declarations that belong on the
 * document root (EPCIS, CBV, GS1 and similar well-known namespaces).
 *
 * <p>Skipping a declaration is only safe while no element or attribute of the fragment uses its
 * prefix: the document root a fragment ends up in does not necessarily declare every well-known
 * namespace (the reactive XML-to-XML path writes its own header with only epcis and xsi). So the
 * writer tracks the bindings it has actually written and declares a prefix on the element or
 * attribute that uses it whenever it is not yet bound inside the fragment.
 */
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
    private final Set<String> requiredNamespaceUris;

    // prefix -> uri declarations written per open element, innermost first
    private final Deque<Map<String, String>> scopes = new ArrayDeque<>();
    // an empty element's start tag is open until the next structural call
    private boolean emptyElementOpen;

    public NonEPCISNamespaceXMLStreamWriter(XMLStreamWriter delegate) {
        this(delegate, Set.of(), Set.of());
    }

    public NonEPCISNamespaceXMLStreamWriter(XMLStreamWriter delegate, Set<String> documentNamespaceUris) {
        this(delegate, documentNamespaceUris, Set.of());
    }

    public NonEPCISNamespaceXMLStreamWriter(final XMLStreamWriter delegate,
                                            final Set<String> documentNamespaceUris,
                                            final Set<String> requiredNamespaceUris) {
        super(delegate);
        this.documentNamespaceUris = documentNamespaceUris != null ? Set.copyOf(documentNamespaceUris) : Set.of();
        this.requiredNamespaceUris = requiredNamespaceUris != null ? Set.copyOf(requiredNamespaceUris) : Set.of();
    }

    @Override
    public void writeNamespace(final String prefix, final String uri) throws XMLStreamException {
        // A namespace this event really uses must be bound, even a well known one such as gs1
        if(requiredNamespaceUris.contains(uri)){
            writeAndRecordNamespace(prefix, uri);
            return;
        }

        // A skipped declaration is not recorded: should the fragment use the prefix after all,
        // the element or attribute using it declares it (see bindIfUnbound)
        if (!SKIP_NAMESPACE_URIS.contains(uri) && !documentNamespaceUris.contains(uri)) {
            writeAndRecordNamespace(prefix, uri);
        }
    }

    // ---------------------------------------------------------------- binding bookkeeping

    private void openElement(final boolean empty) {
        closeEmptyElement();
        scopes.push(new HashMap<>());
        emptyElementOpen = empty;
    }

    private void closeEmptyElement() {
        if (emptyElementOpen) {
            emptyElementOpen = false;
            scopes.pop();
        }
    }

    private boolean isBound(final String prefix, final String uri) {
        for (final Map<String, String> scope : scopes) {
            final String bound = scope.get(prefix);
            if (bound != null) {
                return bound.equals(uri);
            }
        }
        return false;
    }

    private void bindIfUnbound(final String prefix, final String uri) throws XMLStreamException {
        if (prefix == null || prefix.isEmpty() || uri == null || uri.isEmpty()
                || "xml".equals(prefix) || "xmlns".equals(prefix) || scopes.isEmpty()) {
            return;
        }
        if (!isBound(prefix, uri)) {
            delegate.writeNamespace(prefix, uri);
            scopes.peek().put(prefix, uri);
        }
    }

    private void writeAndRecordNamespace(final String prefix, final String uri) throws XMLStreamException {
        // the element itself or one of its attributes may have bound it already
        if (!scopes.isEmpty() && uri != null && uri.equals(scopes.peek().get(prefix))) {
            return;
        }
        delegate.writeNamespace(prefix, uri);
        if (!scopes.isEmpty() && prefix != null) {
            scopes.peek().put(prefix, uri);
        }
    }

    // ---------------------------------------------------------------- elements and attributes

    @Override
    public void writeStartElement(final String prefix, final String local, final String uri) throws XMLStreamException {
        openElement(false);
        delegate.writeStartElement(prefix, local, uri);
        bindIfUnbound(prefix, uri);
    }

    @Override
    public void writeStartElement(final String uri, final String local) throws XMLStreamException {
        openElement(false);
        delegate.writeStartElement(uri, local);
    }

    @Override
    public void writeStartElement(final String local) throws XMLStreamException {
        openElement(false);
        delegate.writeStartElement(local);
    }

    @Override
    public void writeEmptyElement(final String prefix, final String local, final String uri) throws XMLStreamException {
        openElement(true);
        delegate.writeEmptyElement(prefix, local, uri);
        bindIfUnbound(prefix, uri);
    }

    @Override
    public void writeEmptyElement(final String uri, final String local) throws XMLStreamException {
        openElement(true);
        delegate.writeEmptyElement(uri, local);
    }

    @Override
    public void writeEmptyElement(final String local) throws XMLStreamException {
        openElement(true);
        delegate.writeEmptyElement(local);
    }

    @Override
    public void writeEndElement() throws XMLStreamException {
        closeEmptyElement();
        delegate.writeEndElement();
        if (!scopes.isEmpty()) {
            scopes.pop();
        }
    }

    @Override
    public void writeAttribute(final String prefix, final String uri, final String local, final String value)
            throws XMLStreamException {
        delegate.writeAttribute(prefix, uri, local, value);
        bindIfUnbound(prefix, uri);
    }

    // ---------------------------------------------------------------- content closes an empty element

    @Override
    public void writeCharacters(final String text) throws XMLStreamException {
        closeEmptyElement();
        delegate.writeCharacters(text);
    }

    @Override
    public void writeCharacters(final char[] text, final int start, final int len) throws XMLStreamException {
        closeEmptyElement();
        delegate.writeCharacters(text, start, len);
    }

    @Override
    public void writeCData(final String data) throws XMLStreamException {
        closeEmptyElement();
        delegate.writeCData(data);
    }

    @Override
    public void writeComment(final String data) throws XMLStreamException {
        closeEmptyElement();
        delegate.writeComment(data);
    }

    @Override
    public void writeProcessingInstruction(final String target) throws XMLStreamException {
        closeEmptyElement();
        delegate.writeProcessingInstruction(target);
    }

    @Override
    public void writeProcessingInstruction(final String target, final String data) throws XMLStreamException {
        closeEmptyElement();
        delegate.writeProcessingInstruction(target, data);
    }

    @Override
    public void writeEntityRef(final String name) throws XMLStreamException {
        closeEmptyElement();
        delegate.writeEntityRef(name);
    }

    @Override
    public void writeEndDocument() throws XMLStreamException {
        closeEmptyElement();
        delegate.writeEndDocument();
    }
}
