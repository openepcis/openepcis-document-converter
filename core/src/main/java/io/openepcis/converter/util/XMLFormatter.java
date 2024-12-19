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
package io.openepcis.converter.util;

import io.openepcis.converter.exception.FormatConverterException;
import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public class XMLFormatter {
  public String format(String xml) {
    try {
      final TransformerFactory factory = TransformerFactory.newInstance();

      final Transformer transformer = factory.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "yes");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      transformer.setOutputProperty(OutputKeys.STANDALONE, "no");
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

      StringWriter stringWriter = new StringWriter();
      transformer.transform(
          new StreamSource(new StringReader(xml)), new StreamResult(stringWriter));
      return stringWriter.toString();
    } catch (Exception e) {
      throw new FormatConverterException("Formatting of XML failed : " + e.getMessage() + e, e);
    }
  }
}
