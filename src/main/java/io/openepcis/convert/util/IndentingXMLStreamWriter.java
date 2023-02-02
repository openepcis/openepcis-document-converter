package io.openepcis.convert.util;

import java.util.Stack;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public class IndentingXMLStreamWriter extends DelegatingXMLStreamWriter {

  private enum State {
    NONE,
    CHARACTERS,
    ELEMENT
  }

  private State currentState = State.NONE;
  private Stack<State> stateStack = new Stack<>();
  private int indentDepth = 0;
  private String indentString = "   ";

  public IndentingXMLStreamWriter(XMLStreamWriter writer) {
    super(writer);
  }

  private void onStartElement() throws XMLStreamException {
    stateStack.push(State.ELEMENT);
    currentState = State.NONE;
    if (indentDepth > 0) {
      super.writeCharacters("\n");
    }
    indent();
    indentDepth++;
  }

  private void onEndElement() throws XMLStreamException {
    indentDepth--;
    if (currentState == State.ELEMENT) {
      super.writeCharacters("\n");
      indent();
    }
    currentState = stateStack.pop();
  }

  private void onEmptyElement() throws XMLStreamException {
    currentState = State.ELEMENT;
    if (indentDepth > 0) {
      super.writeCharacters("\n");
    }
    indent();
  }

  private void indent() throws XMLStreamException {
    if (indentDepth > 0) {
      for (int i = 0; i < indentDepth; i++) super.writeCharacters(indentString);
    }
  }

  public void writeStartDocument() throws XMLStreamException {
    super.writeStartDocument();
    super.writeCharacters("\n");
  }

  public void writeStartDocument(String version) throws XMLStreamException {
    super.writeStartDocument(version);
    super.writeCharacters("\n");
  }

  public void writeStartDocument(String encoding, String version) throws XMLStreamException {
    super.writeStartDocument(encoding, version);
    super.writeCharacters("\n");
  }

  public void writeStartElement(String localName) throws XMLStreamException {
    onStartElement();
    super.writeStartElement(localName);
  }

  public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
    onStartElement();
    super.writeStartElement(namespaceURI, localName);
  }

  public void writeStartElement(String prefix, String localName, String namespaceURI)
      throws XMLStreamException {
    onStartElement();
    super.writeStartElement(prefix, localName, namespaceURI);
  }

  public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
    onEmptyElement();
    super.writeEmptyElement(namespaceURI, localName);
  }

  public void writeEmptyElement(String prefix, String localName, String namespaceURI)
      throws XMLStreamException {
    onEmptyElement();
    super.writeEmptyElement(prefix, localName, namespaceURI);
  }

  public void writeEmptyElement(String localName) throws XMLStreamException {
    onEmptyElement();
    super.writeEmptyElement(localName);
  }

  public void writeEndElement() throws XMLStreamException {
    onEndElement();
    super.writeEndElement();
  }

  public void writeCharacters(String text) throws XMLStreamException {
    currentState = State.CHARACTERS;
    super.writeCharacters(text);
  }

  public void writeCharacters(char[] text, int start, int len) throws XMLStreamException {
    currentState = State.CHARACTERS;
    super.writeCharacters(text, start, len);
  }

  public void writeCData(String data) throws XMLStreamException {
    currentState = State.CHARACTERS;
    super.writeCData(data);
  }
}
