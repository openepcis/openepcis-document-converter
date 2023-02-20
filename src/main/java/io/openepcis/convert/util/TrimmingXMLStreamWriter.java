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
