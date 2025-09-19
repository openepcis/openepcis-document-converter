# More cURL Examples

## ObjectEvent with unknown GCP-Length 

### Working EPCIS 2.0 XML to JSON (GCP-Length implied by URN Format)

````shell
curl -X 'POST' \
 'http://localhost:8080/api/convert/json/2.0' \
 -H 'accept: application/ld+json' \
 -H 'Content-Type: application/xml' \
 -H 'GS1-EPC-Format: Always_GS1_Digital_Link' \
 -d '<?xml version="1.0" encoding="UTF-8"?>
<epcis:EPCISDocument xmlns:epcis="urn:epcglobal:epcis:xsd:2" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:cbv="https://ref.gs1.org/cbv/" schemaVersion="2.0" creationDate="2023-04-14T12:00:00.000+01:00" xsi:schemaLocation="urn:epcglobal:epcis:xsd:2 EPCglobal-epcis-2_0.xsd">
	<EPCISBody>
		<EventList>
			<ObjectEvent>
				<eventTime>2023-04-14T12:00:00+01:00</eventTime>
				<eventTimeZoneOffset>+01:00</eventTimeZoneOffset>
				<epcList>
					<epc>urn:epc:id:sgtin:7610032.000001.987</epc>
				</epcList>
				<action>OBSERVE</action>
				<bizStep>cbv:BizStep-inspecting</bizStep>
				<disposition>cbv:Disp-in_progress</disposition>
				<readPoint>
					<id>urn:epc:id:sgln:7610032.00005.0</id>
				</readPoint>
				<bizTransactionList>
					<bizTransaction type="cbv:BTT-desadv">urn:epcglobal:cbv:bt:4012345000009:ASN1099</bizTransaction>
				</bizTransactionList>
			</ObjectEvent>
		</EventList>
	</EPCISBody>
</epcis:EPCISDocument>'
````

### Unknown GCP-Length for EPCIS 2.0 GS1 Digital Link in JSON

this should return an error due to unknown GCP-Length when requesting *GS1-EPC-Format: Always_EPC_URN*.

````shell
curl -X 'POST' \
 'http://localhost:8080/api/convert/xml/2.0' \
 -H 'accept: application/xml' \
 -H 'Content-Type: application/ld+json' \
 -H 'GS1-CBV-XML-Format: Always_URN' \
 -H 'GS1-EPC-Format: Always_EPC_URN' \
 -d '{
  "@context": [
    "https://ref.gs1.org/standards/epcis/epcis-context.jsonld"
  ],
  "type": "EPCISDocument",
  "schemaVersion": "2.0",
  "creationDate": "2023-04-14T12:00:00.000+01:00",
  "epcisBody": {
    "eventList": [
      {
        "type": "ObjectEvent",
        "eventTime": "2023-04-14T12:00:00+01:00",
        "eventTimeZoneOffset": "+01:00",
        "epcList": [
          "https://id.gs1.org/01/07610032000010/21/987"
        ],
        "action": "OBSERVE",
        "bizStep": "inspecting",
        "disposition": "in_progress",
        "readPoint": {
          "id": "https://id.gs1.org/414/7610032000058"
        },
        "bizTransactionList": [
          {
            "type": "desadv",
            "bizTransaction": "urn:epcglobal:cbv:bt:4012345000009:ASN1099"
          }
        ]
      }
    ]
  }
}'
````

### No need for GCP-Length when keeping EPCIS 2.0 GS1 Digital Link in JSON as is

this should be working, no GCP-Length required when requesting without *GS1-EPC-Format: Always_EPC_URN*.

````shell
curl -X 'POST' \
 'http://localhost:8080/api/convert/xml/2.0' \
 -H 'accept: application/xml' \
 -H 'Content-Type: application/ld+json' \
 -d '{
  "@context": [
    "https://ref.gs1.org/standards/epcis/epcis-context.jsonld"
  ],
  "type": "EPCISDocument",
  "schemaVersion": "2.0",
  "creationDate": "2023-04-14T12:00:00.000+01:00",
  "epcisBody": {
    "eventList": [
      {
        "type": "ObjectEvent",
        "eventTime": "2023-04-14T12:00:00+01:00",
        "eventTimeZoneOffset": "+01:00",
        "epcList": [
          "https://id.gs1.org/01/07610032000010/21/987"
        ],
        "action": "OBSERVE",
        "bizStep": "inspecting",
        "disposition": "in_progress",
        "readPoint": {
          "id": "https://id.gs1.org/414/7610032000058"
        },
        "bizTransactionList": [
          {
            "type": "desadv",
            "bizTransaction": "urn:epcglobal:cbv:bt:4012345000009:ASN1099"
          }
        ]
      }
    ]
  }
}'
````