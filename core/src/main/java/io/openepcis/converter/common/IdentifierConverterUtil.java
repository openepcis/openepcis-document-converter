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
package io.openepcis.converter.common;

import io.openepcis.converter.exception.FormatConverterException;
import io.openepcis.epc.translator.util.ConverterUtil;
import java.util.Map;

public class IdentifierConverterUtil {

  public static final String toWebURI(final String urn) {
    try {
      // Differentiate between Instance/Class level URN identifiers & call the respective
      // method for conversion
      if (urn.contains(":idpat:") || urn.contains(":class:")) {
        return ConverterUtil.toURIForClassLevelIdentifier(urn);
      } else {
        return ConverterUtil.toURI(urn);
      }
    } catch (Exception exception) {
      throw new FormatConverterException(exception);
    }
  }

  public static Map<String, String> toURN(final String webURI) {
    Map<String, String> convertedURN;
    try {
      // Try to convert WebURI into URN assuming the WebURI is Instance level identifier

      // If WebURI contains the space character then consider the digit after space as GCP
      // Length
      if (webURI.contains(" ")) {
        // Get the GCP Length provided after the identifier by separating the space
        String gcpLength = webURI.substring(webURI.indexOf(" ") + 1);

        // Call the method to convert to URN from Instance WebURI by providing the WebURI
        // and
        // GCPLength
        convertedURN = ConverterUtil.toURN(webURI.split(" ")[0], Integer.parseInt(gcpLength));
      } else {
        // If Class WebURI does not contain whitespace then directly convert to URN
        convertedURN = ConverterUtil.toURN(webURI);
      }
    } catch (Exception exception) {
      // If error is thrown during Instance level Web URI conversion then try to convert
      // the Web URI into Class level URN
      try {

        // If WebURI contains the space character then consider the digit after space as
        // GCP Length
        if (webURI.contains(" ")) {
          // Get the GCP Length provided after the identifier by separating the space
          String gcpLength = webURI.substring(webURI.indexOf(" ") + 1);

          // Call the method to convert to URN from Class WebURI by providing the WebURI
          // and GCPLength
          convertedURN =
              ConverterUtil.toURNForClassLevelIdentifier(
                  webURI.split(" ")[0], Integer.parseInt(gcpLength));
        } else {
          // If Class WebURI does not contain whitespace then directly convert to URN
          convertedURN = ConverterUtil.toURNForClassLevelIdentifier(webURI);
        }
      } catch (Exception e) {
        // If error is thrown during both conversion then show both message to the user
        throw new FormatConverterException(
            "Instance Level Identifiers : "
                + exception.getMessage()
                + System.lineSeparator()
                + System.lineSeparator()
                + "Class Level Identifier : "
                + e.getMessage(),
            e);
      }
    }
    return convertedURN;
  }
}
