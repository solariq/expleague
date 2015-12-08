package com.tbts.xmpp;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.namespace.QName;
import java.io.StringWriter;

/**
 * User: solar
 * Date: 06.12.15
 * Time: 13:53
 */
public abstract class Stanza {
  @XmlAttribute(name = "from")
  private JID from;
  @XmlAttribute(name = "to")
  private JID to;

  public JID from() {
    return from;
  }

  public JID to() {
    return to;
  }

  private static final JAXBContext contextA;

  static {
    try {
      contextA = JAXBContext.newInstance(Stanza.class);
    } catch (JAXBException e) {
      throw new RuntimeException(e);
    }
  }

  public abstract String name();
  public String ns() {
    return "jabber:client";
  }

  public String toString() {
    //noinspection unchecked
    try {
      StringWriter writer = new StringWriter();
      JAXBContext context = JAXBContext.newInstance(getClass());
      Marshaller m = context.createMarshaller();
      m.setProperty(Marshaller.JAXB_FRAGMENT, true);
      //noinspection unchecked
      m.marshal(new JAXBElement(new QName(ns(), name()), getClass(), this), writer);
      return writer.toString();
    } catch (JAXBException e) {
      throw new RuntimeException(e);
    }
  }
}
