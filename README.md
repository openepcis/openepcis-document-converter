[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# OpenEPCIS document format converter

An open-source application that transforms EPCIS documents from XML to JSON/JSON-LD format quickly and effortlessly.

## Introduction

The supply chain system can consist of a single organisation or multiple numbers of them, that are involved in providing clients or consumers with services or products. All physical and digital products progress through some form of supply chain activities before reaching the possession of customers/clients. The accessibility and visibility of the product data throughout its life cycle in the supply chain is the primary concern for the organisation as it needs to track the products in real-time and if required convey the relevant information to customers or other organisations. EPCIS is an ISO (International Organisation for Standardization), IEC (International Electrotechnical Commission), and GS1 Standard that helps to generate and exchange the visibility data within an organisation or across multiple organisations based on the business context. The initial version of EPCIS 1.0 was released at the beginning of 2007. Later, EPCIS 1.1 and 1.2 were released in 2014 and 2016 respectively with various enhancements. Until EPCIS 1.2 events were created only in the XML format. The most recent version of EPCIS, i.e. EPCIS 2.0 has a number of additional improvements including event representation in JSON/JSON-LD format.


## Need for EPCIS format converter:
Since EPCIS is a global standard, it has been adopted by a wide range of business sectors, including those in the automobile, food, healthcare, retail, and many others. As EPCIS 2.0 supports both XML and JSON-LD formats, there is a need to convert the large EPCIS event document from one format to another. This conversion can be useful for organizations to exchange data easily, maintain a single standard format throughout the application, and for various other convenience purposes. Although there are many open-source, ready-to-use software that can convert between these formats available both online and offline, they might not be able to reliably convert the typical EPCIS events. Some EPCIS properties, such userExtensions and sensorElements, and many other attributes must be treated carefully and in accordance with standards throughout the conversion. As a result, there is currently no converter that can convert the huge EPCIS event document between XML-to-JSON-LD and JSON-LD-to-XML formats effectively and efficiently.

By offering a way to easily convert EPCIS events from one format to another, we hope to assist the EPCIS community. The application has been designed to effectively and efficiently transform the bulk EPCIS events. Regardless of the industry, use case, or supply chain system, the application offers a single solution for transforming EPCIS events. The converted EPCIS events are compliant with EPCIS 2.0, the most recent version of the EPCIS.

## Usage:

Following section provides quick overview of how to convert the EPCIS document from one format to other:

### Converting XML to JSON/JSON-LD document

If you have a EPCIS 2.0 XML document which you want to convert to JSON/JSON-LD then provide it as `InputStream` to convert method of [XmlToJsonConverter.class](/blob/main/src/main/java/io/openepcis/epc/formatconverter/XmlToJsonConverter.java):
```
    final ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    final EventJSONStreamCollector collector = new EventJSONStreamCollector(jsonOutput);
    final EventHandler handler = new EventHandler(new EventValidator(), collector);
    new XmlToJsonConverter().convert(xmlStream, handler);
    System.out.println(jsonOutput.toString());
```

### Converting JSON/JSON-LD to XML document

If you have a EPCIS 2.0 JSON/JSON-LD document which you want to convert to XML then provide it as `InputStream` to convert method of [JsonToXmlConverter.class](/blob/main/src/main/java/io/openepcis/epc/formatconverter/JsonToXmlConverter.java) :
```
    final ByteArrayOutputStream xmlOutput = new ByteArrayOutputStream();
    final EventJSONStreamCollector collector = new EventJSONStreamCollector(xmlOutput);
    final EventHandler handler = new EventHandler(new EventValidator(), collector);
    new JsonToXmlConverter().convert(jsonStream, handler);
    System.out.println(out.toString());
```

### Web application

By providing either an XML or JSON/JSON-LD EPCIS document as input, users can easily access and obtain the transformed EPCIS document using the web application. You can access the web tool from [here](https://tools.openepcis.io/openepcis-ui/Documentconverter).

### Swagger-UI:
Users/develoers can make use of the API to send requests to the OpenEPCIS document format converter API using an EPCIS document as the input, and to receive the converted document back as a response. These APIs can also be utilized from within another application’s code or directly online. Users can access the REST endpoint using Swagger-UI from [here](https://tools.openepcis.io/q/swagger-ui/#/Document%20Converter%20Resource).

## Dependencies

The event conversion logic depends on the [openepcis-models](https://github.com/openepcis/openepcis-models/tree/main/epcis) package.