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
package io.openepcis.quarkus.deployment.convert.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.openepcis.converter.VersionTransformer;
import io.openepcis.quarkus.converter.runtime.VersionTransformerProducer;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class VersionTransformerTest {

  @RegisterExtension
  static final QuarkusUnitTest TEST =
      new QuarkusUnitTest().setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

  @Inject VersionTransformerProducer versionTransformerProducer;

  @Inject VersionTransformer versionTransformer;

  @Test
  void testVersionTransformerInjection() throws Exception {
    assertNotNull(versionTransformerProducer);
    assertNotNull(versionTransformer);
    assertNotNull(versionTransformer.getXmlToJsonConverter());
    assertNotNull(versionTransformer.getJsonToXmlConverter());
    assertNotNull(versionTransformer.getXmlVersionTransformer());
  }
}
