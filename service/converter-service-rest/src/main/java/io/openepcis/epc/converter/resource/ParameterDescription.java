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
}
