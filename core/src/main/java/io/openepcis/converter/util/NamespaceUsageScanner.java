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

import com.fasterxml.jackson.databind.JsonNode;
import io.openepcis.constants.EPCIS;
import io.openepcis.model.epcis.util.ConversionNamespaceContext;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the namespaces an event actually uses, so a well known prefix is bound only where needed.
 */
public class NamespaceUsageScanner {
    private NamespaceUsageScanner() {
    }

    /**
     * Namespace URIs of every prefixed field name in this event node.
     */
    public static Set<String> scan(final JsonNode node, final ConversionNamespaceContext nsContext) {
        final Set<String> uris = new HashSet<>();
        collect(node, nsContext, uris);
        return uris;
    }

    private static void collect(final JsonNode node, final ConversionNamespaceContext nsContext, final Set<String> uris) {
        if (node.isObject()) {
            final Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                final String name = names.next();
                final int colon = name.indexOf(':');

                // "@context" and ordinary EPCIS fields carry no prefix, a prefixed extension key does
                if (colon > 0 && !name.startsWith("@")) {
                    final String prefix = name.substring(0, colon);
                    final Object defaultNs = EPCIS.EPCIS_DEFAULT_NAMESPACES.get(prefix);

                    nsContext.findNamespaceByPrefix(prefix)
                            .or(() -> Optional.ofNullable(defaultNs).map(Object::toString))
                            .ifPresent(uri -> {
                                uris.add(uri);
                                // Register it so the mapper writes gs1: instead of inventing ns0:
                                if (defaultNs != null) {
                                    nsContext.populateEventNamespaces(uri, prefix);
                                }
                            });
                }
            }
        }
        // ilmd and nested extension objects hold prefixed keys too
        for (final JsonNode child : node) {
            collect(child, nsContext, uris);
        }
    }
}
