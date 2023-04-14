[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
# OpenEPCIS document format converter

An open-source application that transforms EPCIS documents from XML to JSON/JSON-LD format quickly and effortlessly. Visit [openepcis.io](https://openepcis.io) to find more usesul resources on EPCIS and EPCIS 2.0 Linked Data. Another documentation page is available from [https://openepcis.io/docs/format-converter/](https://openepcis.io/docs/format-converter/).

# Table of Contents
1. [Introduction](#introduction)
2. [Need for EPCIS format converter](#need-for-epcis-format-converter)
3. [Usage](#usage)
   - [Java Code Examples](#java-code-examples)
     - [Converting XML to JSON Document](#converting-xml-to-json-document)
     - [Converting JSON to XML Document](#converting-json-to-xml-document)
   - [Docker Image](#docker-image)
     - [Running with Tools UI Docker](#running-with-tools-ui-docker)
     - [Local Docker cURL Examples](#local-docker-curl-examples)
       - [JSON to EPCIS 2.0 XML](#json-to-epcis-20-xml)
       - [JSON to EPCIS 2.0 XML convert EPC to GS1 Digital Link](#json-to-epcis-20-xml-convert-epc-to-gs1-digital-link)
       - [JSON to EPCIS 2.0 XML convert CBV to Web URI and EPC to GS1 Digital Link](#json-to-epcis-20-xml-convert-cbv-to-web-uri-and-epc-to-gs1-digital-link)
       - [JSON to EPCIS 1.2 XML convert CBV and EPC to URN Format](#json-to-epcis-12-xml-convert-cbv-and-epc-to-urn-format)
       - [EPCIS 1.2 XML to EPCIS 2.0 JSON convert EPC to GS1 Digital Link WebURI Format](#epcis-12-xml-to-epcis-20-json-convert-epc-to-gs1-digital-link-weburi-format)
     - [Additional cURL Examples](#additional-curl-examples)
   - [Web Application](#web-pplication)
   - [Swagger-UI](#swagger-ui)
4. [Dependencies](#dependencies)

## Introduction

The supply chain system can consist of a single organisation or multiple numbers of them, that are involved in providing clients or consumers with services or products. All physical and digital products progress through some form of supply chain activities before reaching the possession of customers/clients. The accessibility and visibility of the product data throughout its life cycle in the supply chain is the primary concern for the organisation as it needs to track the products in real-time and if required convey the relevant information to customers or other organisations. EPCIS is an ISO (International Organisation for Standardization), IEC (International Electrotechnical Commission), and GS1 Standard that helps to generate and exchange the visibility data within an organisation or across multiple organisations based on the business context. The initial version of EPCIS 1.0 was released at the beginning of 2007. Later, EPCIS 1.1 and 1.2 were released in 2014 and 2016 respectively with various enhancements. Until EPCIS 1.2 events were created only in the XML format. The most recent version of EPCIS, i.e. EPCIS 2.0 has a number of additional improvements including event representation in JSON/JSON-LD format.


### Need for EPCIS format converter
Since EPCIS is a global standard, it has been adopted by a wide range of business sectors, including those in the automobile, food, healthcare, retail, and many others. As EPCIS 2.0 supports both XML and JSON-LD formats, there is a need to convert the large EPCIS event document from one format to another. This conversion can be useful for organizations to exchange data easily, maintain a single standard format throughout the application, and for various other convenience purposes. Although there are many open-source, ready-to-use software that can convert between these formats available both online and offline, they might not be able to reliably convert the typical EPCIS events. Some EPCIS properties, such userExtensions and sensorElements, and many other attributes must be treated carefully and in accordance with standards throughout the conversion. As a result, there is currently no converter that can convert the huge EPCIS event document between XML-to-JSON-LD and JSON-LD-to-XML formats effectively and efficiently.

By offering a way to easily convert EPCIS events from one format to another, we hope to assist the EPCIS community. The application has been designed to effectively and efficiently transform the bulk EPCIS events. Regardless of the industry, use case, or supply chain system, the application offers a single solution for transforming EPCIS events. The converted EPCIS events are compliant with EPCIS 2.0, the most recent version of the EPCIS.

## Usage

Following section provides quick overview of how to convert the EPCIS document from one format to another:

### Java Code Examples

#### Converting XML to JSON Document

If you have a EPCIS 2.0 XML document which you want to convert to JSON/JSON-LD then provide it as `InputStream` to convert method of [XmlToJsonConverter.class](src/main/java/io/openepcis/convert/xml/XmlToJsonConverter.java):
```
    final ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    final EventJSONStreamCollector collector = new EventJSONStreamCollector(jsonOutput);
    final EventHandler handler = new EventHandler(new EventValidator(), collector);
    new XmlToJsonConverter().convert(xmlStream, handler);
    System.out.println(jsonOutput.toString());
```

#### Converting JSON to XML Document

If you have a EPCIS 2.0 JSON/JSON-LD document which you want to convert to XML then provide it as `InputStream` to convert method of [JsonToXmlConverter.class](src/main/java/io/openepcis/convert/json/JsonToXmlConverter.java) :
```
    final ByteArrayOutputStream xmlOutput = new ByteArrayOutputStream();
    final EventJSONStreamCollector collector = new EventJSONStreamCollector(xmlOutput);
    final EventHandler handler = new EventHandler(new EventValidator(), collector);
    new JsonToXmlConverter().convert(jsonStream, handler);
    System.out.println(out.toString());
```

### Docker Image

The converter functionality was integrated into our [tools.openepcis.io](https://tools.openepcis.io/openepcis-ui/). 
The Docker image used to run it is also publicly available from [hub.docker.com](https://hub.docker.com) here [https://hub.docker.com/r/openepcis/tools-ui](https://hub.docker.com/r/openepcis/tools-ui). 

#### Running with Tools UI Docker

pull docker image
````shell
docker pull openepcis/tools-ui
````

run docker container
````shell
docker run --rm -t --name openepcis-tools-ui -p 9000:9000 openepcis/tools-ui
````

access locally

| Service                        | URL                                                                         |
|--------------------------------|-----------------------------------------------------------------------------|
| OpenEPCIS Tools User Interface | [http://localhost:9000/openepcis-ui/](http://localhost:9000/openepcis-ui/)  |
| OpenAPI Swagger-UI             | [http://localhost:9000/q/swagger-ui/](http://localhost:9000/q/swagger-ui//) |

#### Local Docker cURL Examples

##### JSON to EPCIS 2.0 XML

````shell
curl -X 'POST' \
 'http://localhost:9000/api/convert/xml/2.0' \
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
 'http://localhost:9000/api/convert/xml/2.0' \
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
 'http://localhost:9000/api/convert/xml/2.0' \
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
 'http://localhost:9000/api/convert/xml/1.2' \
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
 'http://localhost:9000/api/convert/json/2.0' \
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

In order not to bloat up this README we have added a few more examples [here](CURL_EXAMPLES.md).

### Web Application

By providing either an XML or JSON/JSON-LD EPCIS document as input, users can easily access and obtain the transformed EPCIS document using the web application.
You can access the web tool from [tools.openepcis.io/openepcis-ui/Documentconverter](https://tools.openepcis.io/openepcis-ui/Documentconverter).

### Swagger UI
Users/develoers can make use of the API to send requests to the OpenEPCIS document format converter API using an EPCIS document as the input, and to receive the converted document back as a response. These APIs can also be utilized from within another application’s code or directly online.
Users can access the REST endpoint using Swagger-UI from [tools.openepcis.io/q/swagger-ui](https://tools.openepcis.io/q/swagger-ui).

## Dependencies

The event conversion logic depends on the [openepcis-models](https://github.com/openepcis/openepcis-models/tree/main/epcis) package.