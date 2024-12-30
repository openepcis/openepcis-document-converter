package io.openepcis.converter.collector.context;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;

import static io.openepcis.converter.collector.context.GS1EgyptContext.GS1_EGYPT_CONTEXT;

/**
 * Class to make the decision based on which respective context is added during XML -> JSON conversion.
 * Either GS1EgyptContext if matches criteria else use Default Context
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ContextLogicDelegator {
    public static CustomContextLogic getJsonContextLogic(final Map<String, String> allNamespaces, final String gs1Extensions) {

        //Check if the namespaces contain the GS1 Egypt related namespaces if so add the custom context
        final boolean isGS1EgyptContext = allNamespaces.containsKey(GS1EgyptContext.GS1_EGYPT_NAMESPACE) || GS1EgyptContext.GS1_EGYPT_PREFIX.equalsIgnoreCase(gs1Extensions);

        // Determine which implementation to return based on context
        if (isGS1EgyptContext) {
            return new GS1EgyptContext();
        }

        // Return a default implementation or null if no specific logic is needed.
        return new DefaultContext();
    }

    // JSON -> XML conversion check if the context belong to GS1 Egypt if so return GS1EgyptContext else return DefaultContext
    public static CustomContextLogic getXmlNamespaceLogic(final String context, final String gs1Extensions) {
        //Check if the JSON context contain the GS1 Egypt related context or provided GS1 Extension matches the GS1 Egypt extension
        final boolean isGS1EgyptContext = GS1_EGYPT_CONTEXT.equalsIgnoreCase(context) || GS1EgyptContext.GS1_EGYPT_PREFIX.equalsIgnoreCase(gs1Extensions);

        //If matches then return GS1 Egypt context else return default context
        if (isGS1EgyptContext) {
            return new GS1EgyptContext();
        }

        return new DefaultContext();
    }
}
