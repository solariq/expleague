package com.tbts.util.xml;

import com.tbts.xmpp.Stream;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * User: solar
 * Date: 13.12.15
 * Time: 12:48
 */
public class LazyNSXMLStreamWriter implements XMLStreamWriter {
  private final XMLStreamWriter delegate;

  public LazyNSXMLStreamWriter(XMLStreamWriter delegate) {
    this.delegate = delegate;
  }

  public void close() throws XMLStreamException {
    delegate.close();
  }

  public void flush() throws XMLStreamException {
    delegate.flush();
  }

  public NamespaceContext getNamespaceContext() {
    return delegate.getNamespaceContext();
  }

  public String getPrefix(String uri) throws XMLStreamException {
    return delegate.getPrefix(uri);
  }

  public Object getProperty(String str) throws IllegalArgumentException {
    return delegate.getProperty(str);
  }

  public void setDefaultNamespace(String uri) throws XMLStreamException {
    delegate.setDefaultNamespace(uri);
  }

  public void setNamespaceContext(NamespaceContext namespaceContext) throws XMLStreamException {
    delegate.setNamespaceContext(namespaceContext);
  }

  public void setPrefix(String prefix, String uri) throws XMLStreamException {
    delegate.setPrefix(prefix, uri);
  }

  public void writeAttribute(String localName, String value) throws XMLStreamException {
    delegate.writeAttribute(localName, value);
  }

  public void writeAttribute(String namespaceURI, String localName, String value) throws XMLStreamException {
    if ("http://www.w3.org/2001/XMLSchema-instance".equals(namespaceURI))
      return;
    delegate.writeAttribute(namespaceURI, localName, value);
  }

  public void writeAttribute(String prefix, String namespaceURI, String localName, String value) throws XMLStreamException {
    writeAttribute(namespaceURI, localName, value);
  }

  public void writeCData(String cdata) throws XMLStreamException {
    delegate.writeCData(cdata);
  }

  public void writeCharacters(String data) throws XMLStreamException {
    delegate.writeCharacters(data);
  }

  public void writeCharacters(char[] data, int start, int len) throws XMLStreamException {
    delegate.writeCharacters(data, start, len);
  }

  public void writeComment(String comment) throws XMLStreamException {
    delegate.writeComment(comment);
  }

  public void writeDTD(String dtd) throws XMLStreamException {
    delegate.writeDTD(dtd);
  }

  public void writeDefaultNamespace(String namespaceURI) throws XMLStreamException {
    delegate.writeDefaultNamespace(namespaceURI);
  }

  public void writeEmptyElement(String localName) throws XMLStreamException {
    delegate.writeEmptyElement(localName);
  }

  public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
    delegate.writeEmptyElement(namespaceURI, localName);
  }

  public void writeEmptyElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
    delegate.writeEmptyElement(prefix, localName, namespaceURI);
  }

  public void writeEndDocument() throws XMLStreamException {
    delegate.writeEndDocument();
  }

  public void writeEndElement() throws XMLStreamException {
    delegate.writeEndElement();
  }

  public void writeEntityRef(String refName) throws XMLStreamException {
    delegate.writeEntityRef(refName);
  }

  public void writeNamespace(String prefix, String namespaceURI) throws XMLStreamException {
    delegate.writeNamespace(prefix, namespaceURI);
  }

  public void writeProcessingInstruction(String target) throws XMLStreamException {
    delegate.writeProcessingInstruction(target);
  }

  public void writeProcessingInstruction(String target, String data) throws XMLStreamException {
    delegate.writeProcessingInstruction(target, data);
  }

  public void writeStartDocument() throws XMLStreamException {
    delegate.writeStartDocument();
  }

  public void writeStartDocument(String version) throws XMLStreamException {
    delegate.writeStartDocument(version);
  }

  public void writeStartDocument(String encoding, String version) throws XMLStreamException {
    delegate.writeStartDocument(encoding, version);
  }

  public void writeStartElement(String localName) throws XMLStreamException {
    delegate.writeStartElement(localName);
  }

  public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
    delegate.writeStartElement(namespaceURI, localName);
  }

  public void writeStartElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
    delegate.writeStartElement(Stream.NS.equals(namespaceURI) ? "stream" : "", localName, namespaceURI);
  }
}
