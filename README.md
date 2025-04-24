[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java CI with Maven](https://github.com/openepcis/openepcis-document-converter/actions/workflows/maven-cli.yml/badge.svg)](https://github.com/openepcis/openepcis-document-converter/actions/workflows/maven-cli.yml)
# OpenEPCIS document format converter

An open-source application that transforms EPCIS documents from XML to JSON/JSON-LD format quickly and effortlessly. Visit [openepcis.io](https://openepcis.io) to find more usesul resources on EPCIS and EPCIS 2.0 Linked Data. Another documentation page is available from [https://openepcis.io/docs/format-converter/](https://openepcis.io/docs/format-converter/).

## Table of Contents

- [Introduction](#introduction)
    - [Need for an EPCIS Format Converter](#need-for-an-epcis-format-converter)
    - [Key Features](#key-features)
    - [Notes on Vocabulary and SBDH Headers and Custom EPCIS Extension](#notes-on-vocabulary-and-sbdh-headers-and-custom-epcis-extension)
- [Project Modules And Artifacts](#project-modules-and-artifacts)
    - [Core Modules](#core-modules)
    - [Quarkus Modules](#quarkus-modules)
    - [Service Modules](#service-modules)
    - [Other Dependencies](#other-dependencies)
    - [Repositories](#repositories)
- [Getting Started](#getting-started)
    - [Installation](#installation)
        - [Why We Offer a Native Docker Image](#why-we-offer-a-native-docker-image)
        - [Running with Docker](#running-with-docker)
        - [Running with Podman](#running-with-podman)
        - [Accessing the Application](#accessing-the-application)
    - [Usage](#usage)
        - [Practical Examples in Swagger UI](#practical-examples-in-swagger-ui)
        - [Local Docker cURL Examples](#local-docker-curl-examples)
        - [Additional cURL Examples](#additional-curl-examples)
        - [Java Code Examples](#java-code-examples)
- [All-In-One Tools Docker Image](#all-in-one-tools-docker-image)
    - [OpenEPCIS Tools](#openepcis-tools)
        - [Format Converter Web Application](#format-converter-web-application)
        - [Swagger UI](#swagger-ui)
    - [Running with Tools UI Docker](#running-with-tools-ui-docker)
    - [Running with Tools UI Podman](#running-with-tools-ui-podman)
    - [Access Local OpenEPCIS Tools Application](#access-local-openepcis-tools-application)
- [How To Get In Contact and Contribute](#how-to-get-in-contact-and-contribute)

## Introduction

The supply chain system, whether comprising a single organization or multiple entities, plays a crucial role in delivering products or services to clients or consumers. All physical and digital products undergo various supply chain activities before reaching customers. Ensuring accessibility and visibility of product data throughout its life cycle in the supply chain is essential for tracking products in real-time and communicating relevant information to customers or other organizations.

EPCIS (Electronic Product Code Information Services) is a standard developed by ISO (International Organization for Standardization), IEC (International Electrotechnical Commission), and GS1. It facilitates the generation and exchange of visibility data within and across organizations based on business context. Since its initial release in 2007, EPCIS has seen several updates, with versions 1.1 and 1.2 released in 2014 and 2016, respectively. The latest version, EPCIS 2.0, introduces several enhancements, including support for JSON/JSON-LD formats in addition to XML.

### Need for an EPCIS Format Converter

EPCIS is a global standard adopted by diverse business sectors, including automotive, food, healthcare, retail, and more. With EPCIS 2.0 supporting both XML and JSON-LD formats, there is a growing need to convert large EPCIS event documents between these formats. This conversion facilitates data exchange, standardization across applications, and other operational conveniences.

While there are existing open-source tools for format conversion, they often fall short in reliably converting typical EPCIS events. Specific properties, such as `userExtensions` and `sensorElements`, along with various attributes, require careful handling to ensure compliance with standards during conversion. Currently, no tool effectively and efficiently handles bulk EPCIS event document conversion between XML and JSON-LD formats.

Our application aims to fill this gap by providing a reliable solution for converting EPCIS events between formats. It is designed to handle large volumes of data efficiently, ensuring compliance with EPCIS 2.0 standards. Regardless of industry, use case, or supply chain system, our application offers a streamlined solution for transforming EPCIS events.

### Key Features

- **Bulk Conversion**: Efficiently converts large EPCIS event documents between XML and JSON-LD formats.
- **Standards Compliance**: Ensures all converted events adhere to EPCIS 2.0 standards.
- **Versatile Application**: Suitable for various industries and supply chain systems.
- **User-Friendly Interface**: Simplifies the conversion process for users.
- **GraalVM-Community Native Builds**: Provides binary executable artifacts for Linux, macOS, and Windows, supporting both amd64 and arm64 architectures.

By providing a robust and efficient EPCIS event format converter, we aim to support the EPCIS community and facilitate smoother data exchanges across different platforms.

### Notes on Vocabulary and SBDH Headers and Custom EPCIS Extension
The OpenEPCIS Document Converter focuses exclusively on converting EPCIS event data.
It does not convert <EPCISMasterData> (Vocabulary) elements or SBDH (Standard Business Document Header) sections — these parts are intentionally skipped during conversion.

In EPCIS 2.0:
Master data should be provided externally via GS1 Digital Link (linkType=masterdata)
SBDH headers, if used, must be handled separately as they are outside the EPCIS event scope
This design supports clean, standards-aligned adoption of EPCIS 2.0 + JSON-LD, and enables better integration with the semantic web.

⚠️ Important: Be cautious when using custom EPCIS 1.x extensions (e.g., gs1ushc). Many of these have not yet been updated for EPCIS 2.0 and may lack essential artifacts such as:
- JSON-LD context definitions
- JSON Schema for validation
- SHACL for RDF support
- Updated XSDs for XML validation

Without these updates, attempting to use such extensions with EPCIS 2.0 can result in compatibility issues and loss of semantic integrity.

## Project Modules And Artifacts

The `openepcis-document-converter` project is composed of several modules, each serving a distinct purpose in the overall functionality of converting EPCIS event documents. Below is a brief description of each module:

### Core Modules

- **core**: This module contains the core functionality for converting EPCIS event documents between XML and JSON/JSON-LD formats, as well as converting EPCIS 1.2 XML documents to EPCIS 2.0 XML documents and vice versa.

### Quarkus Modules

- **quarkus/runtime**: Provides runtime support for the document conversion processes within the Quarkus framework.
- **quarkus/deployment**: Handles the deployment-specific configurations and optimizations for running the converter on the Quarkus platform.

### Service Modules

- **service/converter-service-restassured**: Contains integration tests for the REST service using RestAssured to ensure the reliability and correctness of the conversion services.
- **service/converter-service-rest**: Implements the RESTful web service interface for the document converter, allowing for remote invocation and interaction.
- **service/converter-service-servlet**: Provides a servlet-based interface for the document converter, enabling web-based interaction.
- **service/quarkus-converter-service**: This module creates the executable artifacts, resulting in a fully functional REST application. It includes Swagger UI and all necessary components for deploying the converter in a microservice or cloud environment.

### Other Dependencies

This project relies on a series of dependencies managed centrally to ensure consistency across all modules. Key dependencies include:

- **io.openepcis.quarkus:quarkus-openepcis-model**: The model classes for EPCIS entities within the Quarkus framework.
- **io.openepcis.quarkus:quarkus-openepcis-model-deployment**: Deployment-specific configurations for the EPCIS model in Quarkus.
- **xalan:xalan** and **xalan:serializer**: Libraries for XML processing and transformation.
- **io.openepcis:openepcis-epc-digitallink-translator**: Utilities for converting EPC identifiers between URN and WebURI formats.
- **io.openepcis:openepcis-repository-common**: Common repository utilities supporting URN to WebURI conversions.
- **io.openepcis:openepcis-test-resources**: Test resources for ensuring the quality and correctness of the conversion logic.

### Repositories

The project uses repositories from Sonatype for managing dependencies. The snapshot repository is configured to receive frequent updates, ensuring that the latest development versions are always available:

- **sonatype-staging**: Sonatype Snapshots ([https://s01.oss.sonatype.org/content/repositories/snapshots](https://s01.oss.sonatype.org/content/repositories/snapshots)) with snapshot updates enabled. Snapshots will be created on a frequent basis to provide the latest features and fixes.

## Getting Started

### Installation

This section provides instructions for running the `openepcis-document-converter` using Docker and Podman. We provide two Docker images:
- `ghcr.io/openepcis/quarkus-converter-service:latest`
- `ghcr.io/openepcis/quarkus-converter-service-native:latest` (native executable)

Both images are multiarch and support `amd64` and `arm64`.

#### Why We Offer a Native Docker Image

We offer a native Docker image to provide an optimized experience for our users. On a typical machine, starting the application in JVM mode takes around 2000ms, while the native version starts up in just under 100ms. Although the JVM may show improved performance over extended periods, the native image boasts a significantly faster startup time. This rapid startup is essential in cloud environments, particularly when targeting serverless architectures, where quick scaling and responsiveness are critical. The native Docker image ensures your applications are ready to handle requests almost instantly, enhancing efficiency and performance in dynamic cloud setups.

#### Running with Docker

To run the `quarkus-converter-service` using Docker, follow these steps:

1. **Pull the Docker image:**

   For the standard executable:
    ```sh
    docker pull ghcr.io/openepcis/quarkus-converter-service:latest
    ```

   For the native executable:
    ```sh
    docker pull ghcr.io/openepcis/quarkus-converter-service-native:latest
    ```

2. **Run the Docker container:**

   For the standard executable:
    ```sh
    docker run --rm -ti -p 8080:8080 -p 8443:8443 ghcr.io/openepcis/quarkus-converter-service:latest
    ```

   For the native executable:
    ```sh
    docker run --rm -ti -p 8080:8080 -p 8443:8443 ghcr.io/openepcis/quarkus-converter-service-native:latest
    ```

    - `-p 8080:8080` maps port 8080 for HTTP access.
    - `-p 8443:8443` maps port 8443 for HTTPS access with a self-signed certificate.

#### Running with Podman

To run the `quarkus-converter-service` using Podman, follow these steps:

1. **Pull the Podman image:**

   For the standard executable:
    ```sh
    podman pull ghcr.io/openepcis/quarkus-converter-service:latest
    ```

   For the native executable:
    ```sh
    podman pull ghcr.io/openepcis/quarkus-converter-service-native:latest
    ```

2. **Run the Podman container:**

   For the standard executable:
    ```sh
    podman run --rm -ti -p 8080:8080 -p 8443:8443 ghcr.io/openepcis/quarkus-converter-service:latest
    ```

   For the native executable:
    ```sh
    podman run --rm -ti -p 8080:8080 -p 8443:8443 ghcr.io/openepcis/quarkus-converter-service-native:latest
    ```

    - `-p 8080:8080` maps port 8080 for HTTP access.
    - `-p 8443:8443` maps port 8443 for HTTPS access with a self-signed certificate.

#### Accessing the Application

The application includes Swagger UI for API documentation and testing, accessible at:
- [http://localhost:8080/q/swagger-ui](http://localhost:8080/q/swagger-ui)
- [https://localhost:8443/q/swagger-ui](https://localhost:8443/q/swagger-ui)

### Usage

Following section provides quick overview of how to convert the EPCIS document from one format to another:

#### Practical Examples in Swagger UI

Our Swagger UI comes pre-loaded with practical examples to help you get started quickly. These examples demonstrate various use cases and API interactions, providing a hands-on way to understand and utilize the converter's capabilities. You can easily access and experiment with these examples directly within the Swagger UI interface.

#### Local Docker cURL Examples

In addition to the examples available in Swagger UI, here are some more direct cURL examples. 

##### JSON to EPCIS 2.0 XML

````shell
curl -X 'POST' \
 'http://localhost:8080/api/convert/xml/2.0' \
 -H 'accept: application/xml' \
 -H 'Content-Type: application/ld+json' \
 -d '{
 "@context": ["https://ref.gs1.org/standards/epcis/2.0.0/epcis-context.jsonld"],
 "type": "EPCISDocument",
 "schemaVersion": "2.0",
 "creationDate":"2019-11-01T14:00:00.000+01:00",
 "epcisBody": {
  "eventList": [
    {
       "type": "AssociationEvent",
       "eventTime": "2019-11-01T14:00:00.000+01:00",
       "eventTimeZoneOffset": "+01:00",
       "parentID":"urn:epc:id:grai:4012345.55555.987",
       "childEPCs":["urn:epc:id:giai:4000001.12345"],
       "action": "ADD",
       "bizStep": "assembling",
       "readPoint": {"id": "urn:epc:id:sgln:4012345.00001.0"}
    }
  ]
 }
}'
````

##### JSON to EPCIS 2.0 XML convert EPC to GS1 Digital Link

````shell
curl -X 'POST' \
 'http://localhost:8080/api/convert/xml/2.0' \
 -H 'accept: application/xml' \
 -H 'Content-Type: application/ld+json' \
 -H 'GS1-EPC-Format: Always_GS1_Digital_Link' \
 -d '{
 "@context": ["https://ref.gs1.org/standards/epcis/2.0.0/epcis-context.jsonld"],
 "type": "EPCISDocument",
 "schemaVersion": "2.0",
 "creationDate":"2019-11-01T14:00:00.000+01:00",
 "epcisBody": {
  "eventList": [
    {
       "type": "AssociationEvent",
       "eventTime": "2019-11-01T14:00:00.000+01:00",
       "eventTimeZoneOffset": "+01:00",
       "parentID":"urn:epc:id:grai:4012345.55555.987",
       "childEPCs":["urn:epc:id:giai:4000001.12345"],
       "action": "ADD",
       "bizStep": "assembling",
       "readPoint": {"id": "urn:epc:id:sgln:4012345.00001.0"}
    }
  ]
 }
}'
````

##### JSON to EPCIS 2.0 XML convert CBV to Web URI and EPC to GS1 Digital Link

````shell
curl -X 'POST' \
 'http://localhost:8080/api/convert/xml/2.0' \
 -H 'accept: application/xml' \
 -H 'Content-Type: application/ld+json' \
 -H 'GS1-CBV-XML-Format: Always_Web_URI' \
 -H 'GS1-EPC-Format: Always_GS1_Digital_Link' \
 -d '{
 "@context": ["https://ref.gs1.org/standards/epcis/2.0.0/epcis-context.jsonld"],
 "type": "EPCISDocument",
 "schemaVersion": "2.0",
 "creationDate":"2019-11-01T14:00:00.000+01:00",
 "epcisBody": {
  "eventList": [
    {
       "type": "AssociationEvent",
       "eventTime": "2019-11-01T14:00:00.000+01:00",
       "eventTimeZoneOffset": "+01:00",
       "parentID":"urn:epc:id:grai:4012345.55555.987",
       "childEPCs":["urn:epc:id:giai:4000001.12345"],
       "action": "ADD",
       "bizStep": "assembling",
       "readPoint": {"id": "urn:epc:id:sgln:4012345.00001.0"}
    }
  ]
 }
}'
````

##### JSON to EPCIS 1.2 XML convert CBV and EPC to URN Format

````shell
curl -X 'POST' \
 'http://localhost:8080/api/convert/xml/1.2' \
 -H 'accept: application/xml' \
 -H 'Content-Type: application/ld+json' \
 -H 'GS1-CBV-XML-Format: Always_URN' \
 -H 'GS1-EPC-Format: Always_EPC_URN' \
 -d '{
 "@context": ["https://ref.gs1.org/standards/epcis/2.0.0/epcis-context.jsonld"],
 "type": "EPCISDocument",
 "schemaVersion": "2.0",
 "creationDate":"2019-11-01T14:00:00.000+01:00",
 "epcisBody": {
  "eventList": [
    {
       "type": "AssociationEvent",
       "eventTime": "2019-11-01T14:00:00.000+01:00",
       "eventTimeZoneOffset": "+01:00",
       "parentID":"https://id.gs1.org/8003/4012345555554987",
       "childEPCs":["https://id.gs1.org/8004/400000112345"],
       "action": "ADD",
       "bizStep": "assembling",
       "readPoint": {"id": "https://id.gs1.org/414/4012345000016"}
    }
  ]
 }
}'
````


##### EPCIS 1.2 XML to EPCIS 2.0 JSON convert EPC to GS1 Digital Link WebURI Format

````shell
curl -X 'POST' \
 'http://localhost:8080/api/convert/json/2.0' \
 -H 'accept: application/ld+json' \
 -H 'Content-Type: application/xml' \
 -H 'GS1-EPC-Format: Always_GS1_Digital_Link' \
 -d '<?xml version="1.0" encoding="UTF-8"?><epcis:EPCISDocument xmlns:epcis="urn:epcglobal:epcis:xsd:1" xmlns:xalan="http://xml.apache.org/xslt" schemaVersion="1.2" creationDate="2019-11-01T14:00:00.000+01:00">
    <EPCISBody>
        <EventList>
            <extension>
                <extension>
                    <AssociationEvent>
                        <eventTime>2019-11-01T14:00:00+01:00</eventTime>
                        <eventTimeZoneOffset>+01:00</eventTimeZoneOffset>
                        <parentID>urn:epc:id:grai:4012345.55555.987</parentID>
                        <childEPCs>
      <epc>urn:epc:id:giai:4000001.12345</epc>
   </childEPCs>
                        <action>ADD</action>
                        <bizStep>urn:epcglobal:cbv:bizstep:assembling</bizStep>
                        <readPoint>
      <id>urn:epc:id:sgln:4012345.00001.0</id>
   </readPoint>
                    </AssociationEvent>
                </extension>
            </extension>
        </EventList>
    </EPCISBody>
</epcis:EPCISDocument>'
````

#### Additional cURL Examples

In order not to bloat up this README we have added a few more examples:

- [curl examples](CURL_EXAMPLES.md)

#### Java Code Examples

##### Converting XML to JSON Document

If you have a EPCIS 2.0 XML document which you want to convert to JSON/JSON-LD then provide it as `InputStream` to convert method of [XmlToJsonConverter.class](src/main/java/io/openepcis/convert/xml/XmlToJsonConverter.java):
```
    final ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    final EventJSONStreamCollector collector = new EventJSONStreamCollector(jsonOutput);
    final EventHandler handler = new EventHandler(new EventValidator(), collector);
    new XmlToJsonConverter().convert(xmlStream, handler);
    System.out.println(jsonOutput.toString());
```

##### Converting JSON to XML Document

If you have a EPCIS 2.0 JSON/JSON-LD document which you want to convert to XML then provide it as `InputStream` to convert method of [JsonToXmlConverter.class](src/main/java/io/openepcis/convert/json/JsonToXmlConverter.java) :
```
    final ByteArrayOutputStream xmlOutput = new ByteArrayOutputStream();
    final EventJSONStreamCollector collector = new EventJSONStreamCollector(xmlOutput);
    final EventHandler handler = new EventHandler(new EventValidator(), collector);
    new JsonToXmlConverter().convert(jsonStream, handler);
    System.out.println(out.toString());
```

## All-In-One Tools Docker Image

The converter functionality is integrated into our [tools.openepcis.io](https://tools.openepcis.io/). The Docker image used to run it is also publicly available on [hub.docker.com](https://hub.docker.com) at [https://hub.docker.com/r/openepcis/tools-ui](https://hub.docker.com/r/openepcis/tools-ui).

This container includes our complete toolchain, including some parts not available under the open-source license. Most notably, it contains the `openepcis-document-converter-sax`, a high-performance streaming implementation of the OpenEPCIS document converter. This SAX-based document converter is part of our business edition and can be integrated via Java SPI. Licensing information can be found [here](https://static.openepcis.io/BENELOG_LICENSE-1.0.pdf).

### OpenEPCIS Tools

OpenEPCIS Tools is a suite of web-based tools designed for handling EPCIS (Electronic Product Code Information Services) documents. It includes functionalities for converting, validating, and visualizing EPCIS data. Users can input EPCIS documents in XML or JSON/JSON-LD formats and obtain transformed documents. The tools are accessible via a user-friendly interface and provide an API for integration with other applications.

#### Format Converter Web Application

By providing either an XML or JSON/JSON-LD EPCIS document as input, users can easily access and obtain the transformed EPCIS document using the web application. You can access the web tool from [https://tools.openepcis.io/ui/format-converter](https://tools.openepcis.io/ui/format-converter).

#### Swagger UI

Users and developers can make use of the API to send requests to the OpenEPCIS document format converter API using an EPCIS document as the input and receive the converted document back as a response. These APIs can also be utilized from within another application’s code or directly online. Users can access the REST endpoint using Swagger-UI from [tools.openepcis.io/q/swagger-ui](https://tools.openepcis.io/q/swagger-ui).

### Running with Tools UI Docker

1. **Pull the Docker image:**
    ```shell
    docker pull openepcis/tools-ui
    ```

2. **Run the Docker container:**
    ```shell
    docker run --rm -t --name openepcis-tools-ui -p 9000:9000 openepcis/tools-ui
    ```

### Running with Tools UI Podman

1. **Pull the Podman image from Docker Hub:**
    ```shell
    podman pull docker.io/openepcis/tools-ui
    ```

2. **Run the Podman container:**
    ```shell
    podman run --rm -t --name openepcis-tools-ui -p 9000:9000 docker.io/openepcis/tools-ui
    ```

### Access Local OpenEPCIS Tools Application

| Service                        | URL                                                                        |
|--------------------------------|----------------------------------------------------------------------------|
| OpenEPCIS Tools User Interface | [http://localhost:9000/openepcis-ui/](http://localhost:9000/openepcis-ui/) |
| OpenAPI Swagger-UI             | [http://localhost:9000/q/swagger-ui/](http://localhost:9000/q/swagger-ui/) |

## SPI based approach for building Default or Custom context/namespaces

In our OpenEPCIS document converter project, we leverage the Java Service Provider Interface (SPI) to enhance modularity and extendability. SPI is a Java platform feature that allows
for service provider modules to be discovered and loaded at runtime. It's like using plug-and-play feature for the code logic, where the system can recognize and work with these accessories
automatically. This approach enables our application to be more flexible and scalable by abstracting the core logic of converting documents and allowing for customization without altering the original codebase.

### How SPI is Utilized

During the JSON/JSON-LD ↔ XML conversion we use the SPI for handling context and namespace resolution, as demonstrated by the `ContextHandler` interface. This interface outlines methods for building
JSON-LD contexts (XML -> JSON/JSON-LD conversion using `buildJsonContext` method), populating XML namespaces (JSON/JSON-LD -> XML using `populateXmlNamespaces` method) conversion, and determining the
applicability of a handler (Default/Custom) for given namespaces or contexts using `isContextHandler` method.

We have separate implementations for different standards or custom behaviors. For instance, the `DefaultContextHandler` provides generic handling, and we can define our own custom handler by implementing the `ContextHandler` interface.
Through SPI, application dynamically discovers and uses these implementations, if no match is found then defaults to `DefaultContextHandler`.  The project includes the `document-converter-extensions` artifact as a dependency. This module contains various implementations and configurations for handling `ContextHandler`.

```java
public interface ContextHandler {
    void buildJsonContext(final JsonGenerator jsonGenerator, final Map<String, String> namespaces);

    void populateXmlNamespaces(final DefaultJsonSchemaNamespaceURIResolver namespaceURIResolver);

    boolean isContextHandler(final Map<String, String> namespaces);
}
```

### SPI Implementation Mechanism

1. **Service Provider Configuration File**: We must list the fully qualified names of our `ContextHandler` implementations in a service provider configuration file located within the
   `META-INF/services` directory. This file is named after the fully qualified interface name, which, in our case, is:

```
io.openepcis.converter.collector.context.handler.ContextHandler
```

2. **ServiceLoader API**: At runtime, we employ the `ServiceLoader` API to discover and load available `ContextHandler` implementations. This lets our conversion process dynamically adapt to the
   specifics of the input document, whether it requires default handling or custom logic.

```java
ServiceLoader<ContextHandler> handlers=ServiceLoader.load(ContextHandler.class);
```

3. **Dynamic Handler Resolution**: During the conversion process, the application iterates over discovered `ContextHandler` instances. It selects the appropriate handler based on the namespaces or
   contexts of the document being converted, enabling a flexible conversion mechanism that can be easily extended with new handlers for different standards or requirements.

### Advantages of Using SPI

Using SPI in our document converter offers several advantages:

* **Extensibility**: New context handlers can be easily added without modifying the core conversion logic.
* **Decoupling**: The conversion logic is decoupled from the specifics of different standards, making the codebase cleaner and more maintainable.
* **Adheres to standards**: SPI-based approach aligns with modern software design principles, promoting modularity and high cohesion.

## How To Get In Contact and Contribute

If you have any questions or need support, please contact us via email at [info@openepcis.io](mailto:info@openepcis.io). We welcome contributions from the community. If you would like to contribute:

1. **Create an Issue**: Report bugs or suggest features by creating an issue on our GitHub repository.
2. **Provide a Fix**: Use the standard fork and pull request workflow.
    - Fork the repository.
    - Make your changes in a new branch.
    - Submit a pull request with a detailed description of your changes.

Your input helps us improve and expand the OpenEPCIS Tools, ensuring they meet the needs of all users.
