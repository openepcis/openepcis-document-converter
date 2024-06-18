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
import io.quarkus.deployment.steps.NativeImageSerializationConfigStep;
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

    // as resource bundles
    Stream.of(
            "org.apache.xml.res.XMLErrorResources",
            "org.apache.xml.serializer.utils.SerializerMessages",
            "org.apache.xalan.xsltc.compiler.util.ErrorMessages"
    ).forEach(builder::addResourceBundle);

    // set runtime initialized classes
    Stream.of(
            "io.openepcis.converter.xml.DefaultXmlVersionTransformer",
            "org.apache.xalan.xsltc.trax.TransformerFactoryImpl",
            "org.apache.bcel.util.SyntheticRepository",
            "org.apache.bcel.util.ClassPath"
    ).forEach(builder::addRuntimeInitializedClass);
    return builder.build();
  }

  @BuildStep
  ReflectiveClassBuildItem addReflectiveClassBuildItem() {
    return ReflectiveClassBuildItem.builder(

            // OpenEPCIS classes
            "io.openepcis.converter.VersionTransformer",
            "io.openepcis.converter.xml.DefaultXmlVersionTransformer",
            "io.openepcis.quarkus.converter.runtime.OpenEPCISConverterHealthCheck",
            "io.openepcis.quarkus.converter.runtime.VersionTransformerProducer",

                    // Apache Xalan related classes
            "org.apache.xalan.xsltc.trax.TransformerFactoryImpl",
            "org.apache.xalan.xsltc.trax.SmartTransformerFactoryImpl",
            "org.apache.xalan.xsltc.dom.XSLTCDTMManager"

            // Apache Xalan related internal class
            /*
            "com.sun.org.apache.xalan.internal.xsltc.compiler.ApplyTemplates",
            "com.sun.org.apache.xalan.internal.xsltc.compiler.CallTemplate",
            "com.sun.org.apache.xalan.internal.xsltc.compiler.Choose",
            "com.sun.org.apache.xalan.internal.xsltc.compiler.Copy",
            "com.sun.org.apache.xalan.internal.xsltc.compiler.CopyOf",
            "com.sun.org.apache.xalan.internal.xsltc.compiler.ForEach",
            "com.sun.org.apache.xalan.internal.xsltc.compiler.If",
            "com.sun.org.apache.xalan.internal.xsltc.compiler.Otherwise",
            "com.sun.org.apache.xalan.internal.xsltc.compiler.Output",
            "com.sun.org.apache.xalan.internal.xsltc.compiler.Param",
            "com.sun.org.apache.xalan.internal.xsltc.compiler.Stylesheet",
            "com.sun.org.apache.xalan.internal.xsltc.compiler.Template",
            "com.sun.org.apache.xalan.internal.xsltc.compiler.ValueOf",
            "com.sun.org.apache.xalan.internal.xsltc.compiler.When",
            "com.sun.org.apache.xalan.internal.xsltc.compiler.Whitespace",
            "com.sun.org.apache.xalan.internal.xsltc.compiler.XslAttribute",
            "com.sun.org.apache.xalan.internal.xsltc.compiler.XslElement",
            "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl",
            "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl",

            "com.sun.org.apache.xml.internal.serializer.SerializationHandler",
            "com.sun.org.apache.xml.internal.dtm.DTMAxisIterator",
            "com.sun.org.apache.xml.internal.serializer.SerializationHandler"

             */
            )
            .unsafeAllocated()
            .serialization().methods().fields().constructors()
            .build();
  }
  @BuildStep
  ReflectiveClassBuildItem addReflectiveClassConstructorBuildItem() {
    return ReflectiveClassBuildItem.builder(
            // predefined generated classes from XML Stylesheets
            "io.openepcis.converter.translet.From12To20",
            "io.openepcis.converter.translet.From20To12"
    ).constructors().methods().fields().build();
  }



  @BuildStep
  NativeImageResourcePatternsBuildItem addNativeImageResourceBuildItem() {
    return NativeImageResourcePatternsBuildItem.builder().includeGlobs(
            "org/apache/xml/serializer/*.properties",
            "xalan-conversion/*.xsl",
            "xalan-conversion/**/*",
            "eventSchemas/*"
    ).build();
  }

}
