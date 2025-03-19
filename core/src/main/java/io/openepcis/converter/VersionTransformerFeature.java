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
package io.openepcis.converter;

import java.util.Collections;
import java.util.List;

public enum VersionTransformerFeature {
  EPCIS_1_2_0_INCLUDE_ASSOCIATION_EVENT,
  EPCIS_1_2_0_INCLUDE_PERSISTENT_DISPOSITION,
  EPCIS_1_2_0_INCLUDE_SENSOR_ELEMENT_LIST;

  public static final List<VersionTransformerFeature> enabledFeatures(final Conversion conversion) {
    if (conversion.generateGS1CompliantDocument().isEmpty()
        || conversion.generateGS1CompliantDocument().orElse(false)) {
      return List.of(
          EPCIS_1_2_0_INCLUDE_ASSOCIATION_EVENT,
          EPCIS_1_2_0_INCLUDE_PERSISTENT_DISPOSITION,
          EPCIS_1_2_0_INCLUDE_SENSOR_ELEMENT_LIST);
    }
    return Collections.emptyList();
  }
}
