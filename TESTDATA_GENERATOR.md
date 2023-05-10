# Convert EPCIS 2.0 JSON events created by the OpenEPCIS EPCIS Testdata Generator

## Table of Contents
1. [Setup local containers](#setup-local-containers)
   - [Make sure to use the latest images](#make-sure-to-use-the-latest-images)
   - [Start Testdata Generator Service docker container](#start-testdata-generator-service-docker-container)
   - [Start Tools Service docker container](#start-tools-service-docker-container)
2. [Execute EPCIS event creation mode on testdata-generator and pipe it to the converter tools](#execute-epcis-event-creation-mode-on-testdata-generator-and-pipe-it-to-the-converter-tools)
   - [Convert to EPCIS 2.0 XML](#convert-to-epcis-20-xml)
   - [Convert to EPCIS 1.2 XML](#convert-to-epcis-12-xml)
3. [Credits](#credits)

## Setup local containers

First we need the 2 containers up and running - one for the testdata generator service and the other one for version converter.

### Make sure to use the latest images

##### Update with Podman:

```
podman pull docker.io/openepcis/testdata-generator
podman pull docker.io/openepcis/openepcis/tools-ui
```

##### Update with Docker:

```
docker pull openepcis/testdata-generator
docker pull openepcis/openepcis/tools-ui
```

### Start Testdata Generator Service docker container

#### Running with Podman:

```
podman run --rm -t --name testdata-generator -p 8080:8080 docker.io/openepcis/testdata-generator
```

#### Running with Docker:

```
docker run --rm -t --name testdata-generator -p 8080:8080 openepcis/testdata-generator
```

### Start Tools Service docker container

#### Running with Podman:

```
podman run --rm -t --name openepcis-tools-ui -p 9000:9000 openepcis/tools-ui
```

#### Running with Docker:

```
docker run --rm -t --name openepcis-tools-ui -p 9000:9000 openepcis/tools-ui
```

## Execute EPCIS event creation mode on testdata-generator and pipe it to the converter tools

To create some wonderful EPCIS 2.0 ObjectEvent in JSON-LD notation we will be using the following curl command:

<details>
    <summary>Expand: single curl command to create 5 ObjectEvents</summary>

```shell
curl -X 'POST' \
  'http://localhost:8080/api/generateTestData?pretty=true' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "events": [{
        "nodeId": 1,
        "eventType": "ObjectEvent",
        "eventCount": 5,
        "locationPartyIdentifierSyntax": "URN",
        "ordinaryEvent": true,
        "action": "ADD",
        "eventID": false,
        "eventTime": {
            "timeZoneOffset": "+02:00",
            "fromTime": "2022-10-01T10:22:16+02:00",
            "toTime": "2022-10-31T10:22:16+02:00"
        },
        "businessStep": "COMMISSIONING",
        "disposition": "ACTIVE",
        "referencedIdentifier": [{
            "identifierId": 1,
            "epcCount": 10,
            "classCount": 0
        }],
        "parentReferencedIdentifier": {},
        "outputReferencedIdentifier": []
    }],
    "identifiers": [{
        "identifierId": 1,
        "objectIdentifierSyntax": "URN",
        "instanceData": {
            "sgtin": {
                "identifierType": "sgtin",
                "gcpLength": 10,
                "sgtin": "40584954485984",
                "serialType": "random",
                "randomCount": 10,
                "randomType": "NUMERIC",
                "randomMinLength": 2,
                "randomMaxLength": 10
            }
        },
        "classData": null,
        "parentData": null
    }]
}'
```
</details>

which will return us these 5 ObjectEvents in EPCIS 2.0 JSON-LD notation:

<details>
    <summary>Expand: ObjectEvents created by the Testdata Generator</summary>

```json
{
  "@context": [
    "https://ref.gs1.org/standards/epcis/2.0.0/epcis-context.jsonld"
  ],
  "type": "EPCISDocument",
  "schemaVersion": "2.0",
  "creationDate": "2023-05-10T15:32:44.997649169Z",
  "epcisBody": {
    "eventList": [
      {
        "type": "ObjectEvent",
        "eventTime": "2022-10-30T20:29:46.672Z",
        "eventTimeZoneOffset": "+02:00",
        "epcList": [
          "urn:epc:id:sgtin:0584954485.498.84",
          "urn:epc:id:sgtin:0584954485.498.606",
          "urn:epc:id:sgtin:0584954485.498.73309601",
          "urn:epc:id:sgtin:0584954485.498.48564",
          "urn:epc:id:sgtin:0584954485.498.7067438722",
          "urn:epc:id:sgtin:0584954485.498.160294",
          "urn:epc:id:sgtin:0584954485.498.17461",
          "urn:epc:id:sgtin:0584954485.498.580512427",
          "urn:epc:id:sgtin:0584954485.498.3081749810",
          "urn:epc:id:sgtin:0584954485.498.71217848"
        ],
        "action": "ADD",
        "bizStep": "commissioning",
        "disposition": "active"
      },
      {
        "type": "ObjectEvent",
        "eventTime": "2022-10-23T12:16:20.32Z",
        "eventTimeZoneOffset": "+02:00",
        "epcList": [
          "urn:epc:id:sgtin:0584954485.498.449",
          "urn:epc:id:sgtin:0584954485.498.19368",
          "urn:epc:id:sgtin:0584954485.498.5306238542",
          "urn:epc:id:sgtin:0584954485.498.1299",
          "urn:epc:id:sgtin:0584954485.498.593464947",
          "urn:epc:id:sgtin:0584954485.498.4144",
          "urn:epc:id:sgtin:0584954485.498.903583058",
          "urn:epc:id:sgtin:0584954485.498.316318",
          "urn:epc:id:sgtin:0584954485.498.53",
          "urn:epc:id:sgtin:0584954485.498.010"
        ],
        "action": "ADD",
        "bizStep": "commissioning",
        "disposition": "active"
      },
      {
        "type": "ObjectEvent",
        "eventTime": "2022-10-11T23:28:29.653Z",
        "eventTimeZoneOffset": "+02:00",
        "epcList": [
          "urn:epc:id:sgtin:0584954485.498.02820",
          "urn:epc:id:sgtin:0584954485.498.5208912",
          "urn:epc:id:sgtin:0584954485.498.327470218",
          "urn:epc:id:sgtin:0584954485.498.8716765997",
          "urn:epc:id:sgtin:0584954485.498.7040241147",
          "urn:epc:id:sgtin:0584954485.498.115711773",
          "urn:epc:id:sgtin:0584954485.498.419229749",
          "urn:epc:id:sgtin:0584954485.498.0077",
          "urn:epc:id:sgtin:0584954485.498.86750664",
          "urn:epc:id:sgtin:0584954485.498.848"
        ],
        "action": "ADD",
        "bizStep": "commissioning",
        "disposition": "active"
      },
      {
        "type": "ObjectEvent",
        "eventTime": "2022-10-07T20:51:43.592Z",
        "eventTimeZoneOffset": "+02:00",
        "epcList": [
          "urn:epc:id:sgtin:0584954485.498.695",
          "urn:epc:id:sgtin:0584954485.498.8334293050",
          "urn:epc:id:sgtin:0584954485.498.82",
          "urn:epc:id:sgtin:0584954485.498.31605474",
          "urn:epc:id:sgtin:0584954485.498.0988016",
          "urn:epc:id:sgtin:0584954485.498.83219159",
          "urn:epc:id:sgtin:0584954485.498.34080921",
          "urn:epc:id:sgtin:0584954485.498.965422",
          "urn:epc:id:sgtin:0584954485.498.5613",
          "urn:epc:id:sgtin:0584954485.498.3046"
        ],
        "action": "ADD",
        "bizStep": "commissioning",
        "disposition": "active"
      },
      {
        "type": "ObjectEvent",
        "eventTime": "2022-10-16T07:59:32.891Z",
        "eventTimeZoneOffset": "+02:00",
        "epcList": [
          "urn:epc:id:sgtin:0584954485.498.006628775",
          "urn:epc:id:sgtin:0584954485.498.1314975901",
          "urn:epc:id:sgtin:0584954485.498.96634309",
          "urn:epc:id:sgtin:0584954485.498.95",
          "urn:epc:id:sgtin:0584954485.498.814640449",
          "urn:epc:id:sgtin:0584954485.498.5177224609",
          "urn:epc:id:sgtin:0584954485.498.83",
          "urn:epc:id:sgtin:0584954485.498.5329258021",
          "urn:epc:id:sgtin:0584954485.498.24",
          "urn:epc:id:sgtin:0584954485.498.593890"
        ],
        "action": "ADD",
        "bizStep": "commissioning",
        "disposition": "active"
      }
    ]
  }
}
```

</details>

### Convert to EPCIS 2.0 XML

Let's bring the generated testdata and the converter service together, to convert EPCIS 2.0 JSON-LD to EPCIS 2.0 XML format.

```shell
curl -X 'POST' \
  'http://localhost:8080/api/generateTestData?pretty=true' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "events": [{
        "nodeId": 1,
        "eventType": "ObjectEvent",
        "eventCount": 5,
        "locationPartyIdentifierSyntax": "URN",
        "ordinaryEvent": true,
        "action": "ADD",
        "eventID": false,
        "eventTime": {
            "timeZoneOffset": "+02:00",
            "fromTime": "2022-10-01T10:22:16+02:00",
            "toTime": "2022-10-31T10:22:16+02:00"
        },
        "businessStep": "COMMISSIONING",
        "disposition": "ACTIVE",
        "referencedIdentifier": [{
            "identifierId": 1,
            "epcCount": 10,
            "classCount": 0
        }],
        "parentReferencedIdentifier": {},
        "outputReferencedIdentifier": []
    }],
    "identifiers": [{
        "identifierId": 1,
        "objectIdentifierSyntax": "URN",
        "instanceData": {
            "sgtin": {
                "identifierType": "sgtin",
                "gcpLength": 10,
                "sgtin": "04068194000004",
                "serialType": "random",
                "randomCount": 10,
                "randomType": "NUMERIC",
                "randomMinLength": 2,
                "randomMaxLength": 10
            }
        },
        "classData": null,
        "parentData": null
    }]
}' | \
curl -X 'POST' \
 'http://localhost:9000/api/convert/xml/2.0' \
 -H 'accept: application/xml' \
 -H 'Content-Type: application/ld+json' \
 -H 'GS1-CBV-XML-Format: Always_Web_URI' \
 -H 'GS1-EPC-Format: Always_GS1_Digital_Link' \
 -d @-
```

This will result in the ObjectEvents that were created by the testdata-generator to be converted to the requested EPCIS 2.0 XML format:

<details>
    <summary>Expand: ObjectEvents converted to EPCIS 2.0 XML</summary>

```xml
<?xml version="1.0" encoding="UTF-8"?>
<epcis:EPCISDocument creationDate="2023-05-10T16:07:55.311216205Z" schemaVersion="2.0" xmlns:cbvmda="urn:epcglobal:cbv:mda" xmlns:epcis="urn:epcglobal:epcis:xsd:2" xmlns:sbdh="http://www.unece.org/cefact/namespaces/StandardBusinessDocumentHeader" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <EPCISBody>
        <EventList>
            <ObjectEvent>
                <eventTime>2022-10-29T16:19:39.618Z</eventTime>
                <eventTimeZoneOffset>+02:00</eventTimeZoneOffset>
                <epcList>
                    <epc>https://id.gs1.org/01/04068194000004/21/171840</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/4953647165</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/20798</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/0673443</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/2891025898</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/948075</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/15216</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/7733512740</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/4659310</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/2646178286</epc>
                </epcList>
                <action>ADD</action>
                <bizStep>https://ref.gs1.org/cbv/BizStep-commissioning</bizStep>
                <disposition>https://ref.gs1.org/cbv/Disp-active</disposition>
            </ObjectEvent>
            <ObjectEvent>
                <eventTime>2022-10-27T15:11:53.711Z</eventTime>
                <eventTimeZoneOffset>+02:00</eventTimeZoneOffset>
                <epcList>
                    <epc>https://id.gs1.org/01/04068194000004/21/3160276208</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/50039103</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/05505</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/309</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/1984409170</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/100797062</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/8059</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/4275146</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/5142925</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/4681976842</epc>
                </epcList>
                <action>ADD</action>
                <bizStep>https://ref.gs1.org/cbv/BizStep-commissioning</bizStep>
                <disposition>https://ref.gs1.org/cbv/Disp-active</disposition>
            </ObjectEvent>
            <ObjectEvent>
                <eventTime>2022-10-17T04:48:45.677Z</eventTime>
                <eventTimeZoneOffset>+02:00</eventTimeZoneOffset>
                <epcList>
                    <epc>https://id.gs1.org/01/04068194000004/21/606631</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/618904350</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/1800612054</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/11</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/649</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/04049</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/9474816</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/7534512</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/780</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/71376</epc>
                </epcList>
                <action>ADD</action>
                <bizStep>https://ref.gs1.org/cbv/BizStep-commissioning</bizStep>
                <disposition>https://ref.gs1.org/cbv/Disp-active</disposition>
            </ObjectEvent>
            <ObjectEvent>
                <eventTime>2022-10-29T18:31:50.142Z</eventTime>
                <eventTimeZoneOffset>+02:00</eventTimeZoneOffset>
                <epcList>
                    <epc>https://id.gs1.org/01/04068194000004/21/80011655</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/348</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/30346800</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/5432453</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/6572736</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/17305</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/0541572508</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/873602249</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/6214</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/06</epc>
                </epcList>
                <action>ADD</action>
                <bizStep>https://ref.gs1.org/cbv/BizStep-commissioning</bizStep>
                <disposition>https://ref.gs1.org/cbv/Disp-active</disposition>
            </ObjectEvent>
            <ObjectEvent>
                <eventTime>2022-10-26T15:43:54.475Z</eventTime>
                <eventTimeZoneOffset>+02:00</eventTimeZoneOffset>
                <epcList>
                    <epc>https://id.gs1.org/01/04068194000004/21/12</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/3223609523</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/819</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/2691</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/8695</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/4302032526</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/54988</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/86166</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/6007090</epc>
                    <epc>https://id.gs1.org/01/04068194000004/21/911401385</epc>
                </epcList>
                <action>ADD</action>
                <bizStep>https://ref.gs1.org/cbv/BizStep-commissioning</bizStep>
                <disposition>https://ref.gs1.org/cbv/Disp-active</disposition>
            </ObjectEvent>
        </EventList>
    </EPCISBody>
</epcis:EPCISDocument>
```

</details>

### Convert to EPCIS 1.2 XML

In this example we will have the testdata-generator create all identifiers in WebURI / GS1 DigitalLink notation, 
to illustrate how the converter service can also take care of converting them into backward-compatible EPCIS 1.2 URNs.

This can be achieved by setting:

```
"locationPartyIdentifierSyntax": "WebURI"
```

```
"objectIdentifierSyntax": "WebURI"
```

Setting the syntax to WebURI will return the following EPCIS 2.0 JSON being generated by the testdata-generator:

<details>
    <summary>Expand: ObjectEvents created in EPCIS 2.0 using WebURIs</summary>

```json
{
    "@context": [
        "https://ref.gs1.org/standards/epcis/2.0.0/epcis-context.jsonld"
    ],
    "creationDate": "2023-05-10T16:14:32.445931507Z",
    "epcisBody": {
        "eventList": [
            {
                "action": "ADD",
                "bizStep": "commissioning",
                "disposition": "active",
                "epcList": [
                    "https://id.gs1.org/01/04068194000004/21/5817473817",
                    "https://id.gs1.org/01/04068194000004/21/47931",
                    "https://id.gs1.org/01/04068194000004/21/244",
                    "https://id.gs1.org/01/04068194000004/21/71579781",
                    "https://id.gs1.org/01/04068194000004/21/344684875",
                    "https://id.gs1.org/01/04068194000004/21/39482541",
                    "https://id.gs1.org/01/04068194000004/21/30",
                    "https://id.gs1.org/01/04068194000004/21/73",
                    "https://id.gs1.org/01/04068194000004/21/78969843",
                    "https://id.gs1.org/01/04068194000004/21/64408"
                ],
                "eventTime": "2022-10-10T16:51:11.753Z",
                "eventTimeZoneOffset": "+02:00",
                "type": "ObjectEvent"
            },
            {
                "action": "ADD",
                "bizStep": "commissioning",
                "disposition": "active",
                "epcList": [
                    "https://id.gs1.org/01/04068194000004/21/186729",
                    "https://id.gs1.org/01/04068194000004/21/339",
                    "https://id.gs1.org/01/04068194000004/21/62",
                    "https://id.gs1.org/01/04068194000004/21/8913074829",
                    "https://id.gs1.org/01/04068194000004/21/4802186786",
                    "https://id.gs1.org/01/04068194000004/21/3571684256",
                    "https://id.gs1.org/01/04068194000004/21/418",
                    "https://id.gs1.org/01/04068194000004/21/381",
                    "https://id.gs1.org/01/04068194000004/21/969550354",
                    "https://id.gs1.org/01/04068194000004/21/48086"
                ],
                "eventTime": "2022-10-31T00:07:47.841Z",
                "eventTimeZoneOffset": "+02:00",
                "type": "ObjectEvent"
            },
            {
                "action": "ADD",
                "bizStep": "commissioning",
                "disposition": "active",
                "epcList": [
                    "https://id.gs1.org/01/04068194000004/21/1001544559",
                    "https://id.gs1.org/01/04068194000004/21/6414377",
                    "https://id.gs1.org/01/04068194000004/21/743686393",
                    "https://id.gs1.org/01/04068194000004/21/5656264827",
                    "https://id.gs1.org/01/04068194000004/21/2834",
                    "https://id.gs1.org/01/04068194000004/21/8673432",
                    "https://id.gs1.org/01/04068194000004/21/844",
                    "https://id.gs1.org/01/04068194000004/21/35179051",
                    "https://id.gs1.org/01/04068194000004/21/886",
                    "https://id.gs1.org/01/04068194000004/21/03707"
                ],
                "eventTime": "2022-10-14T04:19:37.074Z",
                "eventTimeZoneOffset": "+02:00",
                "type": "ObjectEvent"
            },
            {
                "action": "ADD",
                "bizStep": "commissioning",
                "disposition": "active",
                "epcList": [
                    "https://id.gs1.org/01/04068194000004/21/26569",
                    "https://id.gs1.org/01/04068194000004/21/19968",
                    "https://id.gs1.org/01/04068194000004/21/1674",
                    "https://id.gs1.org/01/04068194000004/21/0460",
                    "https://id.gs1.org/01/04068194000004/21/9803365796",
                    "https://id.gs1.org/01/04068194000004/21/0734609066",
                    "https://id.gs1.org/01/04068194000004/21/988",
                    "https://id.gs1.org/01/04068194000004/21/030",
                    "https://id.gs1.org/01/04068194000004/21/71001",
                    "https://id.gs1.org/01/04068194000004/21/3263020962"
                ],
                "eventTime": "2022-10-14T13:36:33.471Z",
                "eventTimeZoneOffset": "+02:00",
                "type": "ObjectEvent"
            },
            {
                "action": "ADD",
                "bizStep": "commissioning",
                "disposition": "active",
                "epcList": [
                    "https://id.gs1.org/01/04068194000004/21/471676",
                    "https://id.gs1.org/01/04068194000004/21/826913098",
                    "https://id.gs1.org/01/04068194000004/21/1293553",
                    "https://id.gs1.org/01/04068194000004/21/19658",
                    "https://id.gs1.org/01/04068194000004/21/9927198358",
                    "https://id.gs1.org/01/04068194000004/21/02992551",
                    "https://id.gs1.org/01/04068194000004/21/43",
                    "https://id.gs1.org/01/04068194000004/21/609173502",
                    "https://id.gs1.org/01/04068194000004/21/3194",
                    "https://id.gs1.org/01/04068194000004/21/27"
                ],
                "eventTime": "2022-10-11T04:50:04.037Z",
                "eventTimeZoneOffset": "+02:00",
                "type": "ObjectEvent"
            }
        ]
    },
    "schemaVersion": "2.0",
    "type": "EPCISDocument"
}
```

</details>

Now it's time to execute the curl pipeline:

```shell
curl -X 'POST' \
  'http://localhost:8080/api/generateTestData?pretty=true' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "events": [{
        "nodeId": 1,
        "eventType": "ObjectEvent",
        "eventCount": 5,
        "locationPartyIdentifierSyntax": "WebURI",
        "ordinaryEvent": true,
        "action": "ADD",
        "eventID": false,
        "eventTime": {
            "timeZoneOffset": "+02:00",
            "fromTime": "2022-10-01T10:22:16+02:00",
            "toTime": "2022-10-31T10:22:16+02:00"
        },
        "businessStep": "COMMISSIONING",
        "disposition": "ACTIVE",
        "referencedIdentifier": [{
            "identifierId": 1,
            "epcCount": 10,
            "classCount": 0
        }],
        "parentReferencedIdentifier": {},
        "outputReferencedIdentifier": []
    }],
    "identifiers": [{
        "identifierId": 1,
        "objectIdentifierSyntax": "WebURI",
        "instanceData": {
            "sgtin": {
                "identifierType": "sgtin",
                "gcpLength": 10,
                "sgtin": "04068194000004",
                "serialType": "random",
                "randomCount": 10,
                "randomType": "NUMERIC",
                "randomMinLength": 2,
                "randomMaxLength": 10
            }
        },
        "classData": null,
        "parentData": null
    }]
}' | \
curl -X 'POST' \
 'http://localhost:9000/api/convert/xml/1.2' \
 -H 'accept: application/xml' \
 -H 'Content-Type: application/ld+json' \
 -H 'GS1-CBV-XML-Format: Always_URN' \
 -H 'GS1-EPC-Format: Always_EPC_URN' \
 -d @-
```

Note the HTTP headers being used for instructing the converter service to convert from WebURI to URN notation:

```
GS1-CBV-XML-Format: Always_URN
GS1-EPC-Format: Always_EPC_URN 
```

This will result in the ObjectEvents that were created by the testdata-generator to be converted to the requested EPCIS 1.2 XML format with WebURIs converted to URNs:

<details>
    <summary>Expand: ObjectEvents converted to EPCIS 1.2 XML</summary>

```xml

```

</details>

## Credits

kudos going to 

[Ralph Tröger](https://github.com/ralphTro) for the all the valuable feedback and support.\
[Aravinda Baliga](https://github.com/Aravinda93) for his implementation skills and all the hours he spent on integrating and adjusting.\
[Sven Böckelmann](https://github.com/sboeckelmann) for the technical input and his valuable directions.