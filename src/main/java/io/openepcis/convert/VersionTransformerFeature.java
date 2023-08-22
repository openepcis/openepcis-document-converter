package io.openepcis.convert;

import java.util.Collections;
import java.util.List;

public enum VersionTransformerFeature {

    EPCIS_1_2_0_INCLUDE_ASSOCIATION_EVENT,
    EPCIS_1_2_0_INCLUDE_PERSISTENT_DISPOSITION,
    EPCIS_1_2_0_INCLUDE_SENSOR_ELEMENT_LIST;

    public static final List<VersionTransformerFeature> enabledFeatures(final Conversion conversion) {
        if (conversion.generateGS1CompliantDocument().orElse(false)) {
            return List.of(
                    EPCIS_1_2_0_INCLUDE_ASSOCIATION_EVENT,
                    EPCIS_1_2_0_INCLUDE_PERSISTENT_DISPOSITION,
                    EPCIS_1_2_0_INCLUDE_SENSOR_ELEMENT_LIST);
        }
        return Collections.emptyList();
    }

}
