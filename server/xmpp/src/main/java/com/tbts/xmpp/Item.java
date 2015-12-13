package com.tbts.xmpp;

import com.fasterxml.aalto.stax.OutputFactoryImpl;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * User: solar
 * Date: 08.12.15
 * Time: 17:57
 */
@XmlTransient
public class Item {
  @Override
  public String toString() {
    try {
      final Marshaller marshaller = Stream.marshaller();
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      final OutputFactoryImpl factory = new OutputFactoryImpl();
      factory.configureForSpeed();
      final XMLStreamWriter xmlWriter = factory.createXMLStreamWriter(out);
      marshaller.marshal(this, xmlWriter);
      xmlWriter.close();
      out.close();
      return new String(out.toByteArray());
    }
    catch (JAXBException | IOException | XMLStreamException e) {
      throw new RuntimeException(e);
    }
  }
}
