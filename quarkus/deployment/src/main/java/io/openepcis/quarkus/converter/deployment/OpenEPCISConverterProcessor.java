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
package io.openepcis.quarkus.converter.deployment;

import io.openepcis.converter.VersionTransformer;
import io.openepcis.converter.xml.DefaultXmlVersionTransformer;
import io.openepcis.quarkus.converter.runtime.OpenEPCISConverterHealthCheck;
import io.openepcis.quarkus.converter.runtime.VersionTransformerProducer;
import io.openepcis.quarkus.deployment.model.OpenEPCISBuildTimeConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageConfigBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.smallrye.health.deployment.spi.HealthBuildItem;

import java.util.stream.Stream;

public class OpenEPCISConverterProcessor {

  private static final String FEATURE = "openepcis-document-converter";


  @BuildStep
  FeatureBuildItem feature() {
    return new FeatureBuildItem(FEATURE);
  }

  @BuildStep()
  AdditionalBeanBuildItem buildOpenEPCISJAXBContext() {
    return AdditionalBeanBuildItem.unremovableOf(VersionTransformerProducer.class);
  }

  @BuildStep
  HealthBuildItem addHealthCheck(OpenEPCISBuildTimeConfig buildTimeConfig) {
    return new HealthBuildItem(OpenEPCISConverterHealthCheck.class.getName(),
            buildTimeConfig.healthEnabled);
  }


  @BuildStep
  NativeImageConfigBuildItem addNativeImageConfig() {
    final NativeImageConfigBuildItem.Builder builder = NativeImageConfigBuildItem.builder();
    Stream.of(
            "org.apache.xml.res.XMLErrorResources",
                    "org.apache.xml.serializer.utils.SerializerMessages"
    ).forEach(builder::addResourceBundle);
    return builder.build();
  }

  @BuildStep
  ReflectiveClassBuildItem addReflectiveClassBuildItem() {
    return ReflectiveClassBuildItem.builder(
            VersionTransformerProducer.class,
            VersionTransformer.class,
            DefaultXmlVersionTransformer.class,
            org.apache.xml.dtm.ref.DTMManagerDefault.class,
            org.apache.xml.serializer.AttributesImplSerializer.class,
            org.apache.xml.serializer.DOM3Serializer.class,
            org.apache.xml.serializer.DOMSerializer.class,
            org.apache.xml.serializer.ExtendedContentHandler.class,
            org.apache.xml.serializer.ExtendedLexicalHandler.class,
            org.apache.xml.serializer.ToXMLStream.class
    )
            .unsafeAllocated()
            .serialization().methods().fields().constructors()
            .build();
  }


  @BuildStep
  NativeImageResourcePatternsBuildItem addNativeImageResourceBuildItem() {
    return NativeImageResourcePatternsBuildItem.builder().includeGlobs(
            "org/apache/xml/serializer/*.properties"
    ).build();
  }

}
