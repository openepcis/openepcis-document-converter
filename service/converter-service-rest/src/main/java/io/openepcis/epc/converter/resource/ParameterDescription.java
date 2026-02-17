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
package io.openepcis.epc.converter.resource;

public interface ParameterDescription {
  String GS1_EPC_FORMAT =
      "Header used by the client to indicate whether EPCs are expressed as GS1 Digital Link URIs or as EPC URNs.\n"
          + "It is also used by the server to announce which EPC formats are supported. \n"
          + "- No_Preference: No preference in the representation, i.e. any format is accepted.\n"
          + "- Always_GS1_Digital_Link: URIs are returned as GS1 Digital Link.\n"
          + "- Always_EPC_URN: URIs are returned as URN.\n"
          + "- Never_Translates: EPCs are never translated, i.e. the original format is kept.";
  String GS1_CBV_XML_FORMAT =
      "When requesting XML content-type only, users can use this header to request\n"
          + "receiving events with CBV values in either URN or Web URI format.\n"
          + "This option is not available for JSON/JSON-LD.\n"
          + "- No_Preference: The server chooses the representation.\n"
          + "- Always_Web_URI: CBV values are returned as Web URI.\n"
          + "- Always_URN: CBV values are returned as URNs.\n"
          + "- Never_Translates: The original format is kept.";
  String GS1_EPCIS_1_2_COMPLIANT =
      "Controls whether the generated EPCIS 1.2 XML strictly complies with the GS1 EPCIS 1.2 standard.\n"
          + "When true or not provided, EPCIS 2.0-only elements (AssociationEvent, sensorElementList, "
          + "persistentDisposition) are excluded from the output.\n"
          + "When false, these elements are included as extensions in the EPCIS 1.2 XML.\n"
          + "- true (default): Strict GS1 EPCIS 1.2 compliance - exclude 2.0-only elements.\n"
          + "- false: Include EPCIS 2.0-only elements as extensions in 1.2 output.";
}
