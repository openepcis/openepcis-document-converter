/*
 * Copyright 2022-2023 benelog GmbH & Co. KG
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
package io.openepcis.convert.util;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public class TrimmingXMLStreamWriter extends DelegatingXMLStreamWriter {
  public TrimmingXMLStreamWriter(XMLStreamWriter writer) {
    super(writer);
  }

  @Override
  public void writeCharacters(String text) throws XMLStreamException {
    super.writeCharacters(text.trim());
  }

  @Override
  public void writeCharacters(char[] text, int start, int len) throws XMLStreamException {
    super.writeCharacters(new String(text, start, len).trim());
  }
}
