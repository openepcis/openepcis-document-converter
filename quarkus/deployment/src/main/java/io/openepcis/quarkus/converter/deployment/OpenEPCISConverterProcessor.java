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

import io.openepcis.quarkus.converter.runtime.OpenEPCISConverterHealthCheck;
import io.openepcis.quarkus.converter.runtime.VersionTransformerProducer;
import io.openepcis.quarkus.deployment.model.OpenEPCISBuildTimeConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageConfigBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.smallrye.health.deployment.spi.HealthBuildItem;
import java.util.ArrayList;
import java.util.List;
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
    return new HealthBuildItem(
        OpenEPCISConverterHealthCheck.class.getName(), buildTimeConfig.healthEnabled());
  }

  @BuildStep
  NativeImageConfigBuildItem addNativeImageConfig() {
    final NativeImageConfigBuildItem.Builder builder = NativeImageConfigBuildItem.builder();

    // as resource bundles
    Stream.of(
            "org.apache.xml.res.XMLErrorResources",
            "org.apache.xml.serializer.utils.SerializerMessages",
            "org.apache.xalan.xsltc.compiler.util.ErrorMessages")
        .forEach(builder::addResourceBundle);

    // set runtime initialized classes
    Stream.of(
            "io.openepcis.converter.xml.DefaultXmlVersionTransformer",
            "org.apache.xalan.xsltc.trax.TransformerFactoryImpl",
            "org.apache.bcel.util.SyntheticRepository",
            "org.apache.bcel.util.ClassPath")
        .forEach(builder::addRuntimeInitializedClass);
    return builder.build();
  }

  @BuildStep
  ReflectiveClassBuildItem addReflectiveClassBuildItem() {
    return ReflectiveClassBuildItem.builder(

            // OpenEPCIS classes
            "io.openepcis.converter.VersionTransformer",
            "io.openepcis.converter.xml.DefaultXmlVersionTransformer",
            "io.openepcis.converter.collector.context.impl.DefaultContextHandler",
            "io.openepcis.quarkus.converter.runtime.OpenEPCISConverterHealthCheck",
            "io.openepcis.quarkus.converter.runtime.VersionTransformerProducer",

            // Apache Xalan related classes
            "org.apache.xalan.xsltc.trax.TransformerFactoryImpl",
            "org.apache.xalan.xsltc.trax.SmartTransformerFactoryImpl",
            "org.apache.xalan.xsltc.dom.XSLTCDTMManager")
        .unsafeAllocated()
        .serialization()
        .methods()
        .fields()
        .constructors()
        .build();
  }

  @BuildStep
  ReflectiveClassBuildItem addReflectiveClassConstructorBuildItem() {
    return ReflectiveClassBuildItem.builder(
            // predefined generated classes from XML Stylesheets
            "io.openepcis.converter.translet.From12To20",
            "io.openepcis.converter.translet.From20To12")
        .constructors()
        .methods()
        .fields()
        .build();
  }

  @BuildStep
  NativeImageResourcePatternsBuildItem addNativeImageResourceBuildItem() {
    return NativeImageResourcePatternsBuildItem.builder()
        .includeGlobs(
            "org/apache/xml/serializer/*.properties",
            "xalan-conversion/*.xsl",
            "xalan-conversion/**/*",
            "eventSchemas/*")
        .build();
  }

  @BuildStep
  List<ServiceProviderBuildItem> registerServiceProviders() {
    final List<ServiceProviderBuildItem> providers = new ArrayList<>();

    // ContextHandler (base converter module - always present)
    final List<String> contextHandlers = new ArrayList<>();
    contextHandlers.add("io.openepcis.converter.collector.context.impl.DefaultContextHandler");
    if (isClassAvailable("io.openepcis.gs1eg.context.impl.GS1EgyptContextHandler")) {
      contextHandlers.add("io.openepcis.gs1eg.context.impl.GS1EgyptContextHandler");
    }
    providers.add(
        new ServiceProviderBuildItem(
            "io.openepcis.converter.collector.context.handler.ContextHandler",
            contextHandlers));

    // SAX module providers (conditional on SAX jar being present)
    if (!isClassAvailable("io.openepcis.converter.sax.SAXVersionTransformerFactory")) {
      return providers;
    }

    // Core SAX transformers
    providers.add(
        new ServiceProviderBuildItem(
            "io.openepcis.converter.xml.XmlVersionTransformerFactory",
            "io.openepcis.converter.sax.SAXVersionTransformerFactory"));
    providers.add(
        new ServiceProviderBuildItem(
            "io.openepcis.converter.reactive.ReactiveXmlVersionTransformerFactory",
            "io.openepcis.converter.sax.reactive.ReactiveSAXXmlVersionTransformerFactory"));

    // Element processors
    final List<String> elementProcessors = new ArrayList<>();
    elementProcessors.add(
        "io.openepcis.converter.sax.handler.extensions.impl.DefaultElementProcessor");
    if (isClassAvailable("io.openepcis.gs1eg.extensions.impl.GS1EgyptElementProcessor")) {
      elementProcessors.add("io.openepcis.gs1eg.extensions.impl.GS1EgyptElementProcessor");
    }
    providers.add(
        new ServiceProviderBuildItem(
            "io.openepcis.converter.sax.handler.extensions.handler.ElementProcessor",
            elementProcessors));

    // Namespace processors
    final List<String> namespaceProcessors = new ArrayList<>();
    namespaceProcessors.add(
        "io.openepcis.converter.sax.handler.namespace.impl.DefaultNamespaceProcessor");
    if (isClassAvailable("io.openepcis.gs1eg.namespace.impl.GS1EgyptNamespaceProcessor")) {
      namespaceProcessors.add("io.openepcis.gs1eg.namespace.impl.GS1EgyptNamespaceProcessor");
    }
    providers.add(
        new ServiceProviderBuildItem(
            "io.openepcis.converter.sax.handler.namespace.handler.NamespaceProcessor",
            namespaceProcessors));

    return providers;
  }

  @BuildStep
  ReflectiveClassBuildItem registerSaxReflectiveClasses() {
    if (!isClassAvailable("io.openepcis.converter.sax.SAXVersionTransformerFactory")) {
      return ReflectiveClassBuildItem.builder(new String[0]).build();
    }

    final List<String> classes = new ArrayList<>(List.of(
        // SAX transformer factories and transformers
        "io.openepcis.converter.sax.SAXVersionTransformerFactory",
        "io.openepcis.converter.sax.SAXVersionTransformer",
        "io.openepcis.converter.sax.reactive.ReactiveSAXXmlVersionTransformerFactory",
        "io.openepcis.converter.sax.reactive.ReactiveSAXXmlVersionTransformer",
        "io.openepcis.converter.sax.reactive.ReactiveSAXVersionTransformer",
        // Reactive SAX handlers
        "io.openepcis.converter.sax.reactive.ReactiveEPCIS12Handler",
        "io.openepcis.converter.sax.reactive.ReactiveEPCIS20Handler",
        // Traditional SAX handler factory and handlers
        "io.openepcis.converter.sax.handler.EPCISSaxHandlerFactory",
        "io.openepcis.converter.sax.handler.EPCIS12",
        "io.openepcis.converter.sax.handler.EPCIS20",
        // Default processors (ServiceLoader discovered)
        "io.openepcis.converter.sax.handler.extensions.impl.DefaultElementProcessor",
        "io.openepcis.converter.sax.handler.namespace.impl.DefaultNamespaceProcessor"));

    // Conditionally add GS1 Egypt extension classes
    if (isClassAvailable("io.openepcis.gs1eg.context.impl.GS1EgyptContextHandler")) {
      classes.add("io.openepcis.gs1eg.context.impl.GS1EgyptContextHandler");
    }
    if (isClassAvailable("io.openepcis.gs1eg.extensions.impl.GS1EgyptElementProcessor")) {
      classes.add("io.openepcis.gs1eg.extensions.impl.GS1EgyptElementProcessor");
    }
    if (isClassAvailable("io.openepcis.gs1eg.namespace.impl.GS1EgyptNamespaceProcessor")) {
      classes.add("io.openepcis.gs1eg.namespace.impl.GS1EgyptNamespaceProcessor");
    }

    return ReflectiveClassBuildItem.builder(classes.toArray(String[]::new))
        .constructors()
        .methods()
        .fields()
        .build();
  }

  private static boolean isClassAvailable(String className) {
    try {
      Thread.currentThread().getContextClassLoader().loadClass(className);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
