package com.expleague.xmpp.control.sasl;

import com.expleague.xmpp.Item;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;

/**
 * User: solar
 * Date: 11.12.15
 * Time: 16:08
 */
@XmlRootElement(namespace = "urn:ietf:params:xml:ns:xmpp-sasl")
public class Failure extends Item {
  @XmlAnyElement
  @XmlJavaTypeAdapter(type = JAXBElement.class, value = Type.TypeAdapter.class)
  private Type type;

  @XmlElement(namespace = "urn:ietf:params:xml:ns:xmpp-sasl")
  protected String text;

  public Failure() {}

  public Failure(Type type, String text) {
    this.type = type;
    this.text = text;
  }

  @XmlEnum
  public enum Type {
    ACCOUNT_DISABLED,
    CREDENTIALS_EXPIRED,
    ENCRYPTION_REQUIRED,
    INCORRECT_ENCODING,
    INVALID_AUTHZID,
    INVALID_MECHANISM,
    MALFORMED_REQUEST,
    MECHANISM_TOO_WEAK,
    NOT_AUTHORIZED,
    TEMPORARY_AUTH_FAILURE,
    ABORTED, TRANSITION_NEEDED;
    public static class TypeAdapter extends XmlAdapter<JAXBElement<?>, Type> {
      @Override
      public Type unmarshal(JAXBElement<?> v) throws Exception {
        final String propName = v.getName().getLocalPart().toUpperCase().replace('-', '_');
        return Type.valueOf(propName);
      }

      @Override
      public JAXBElement<?> marshal(Type v) throws Exception {
        final QName qName = new QName("urn:ietf:params:xml:ns:xmpp-sasl", v.name().toLowerCase().replace('_', '-'));
        //noinspection unchecked
        return new JAXBElement(qName, Type.class, null);
      }
    }
  }
}
