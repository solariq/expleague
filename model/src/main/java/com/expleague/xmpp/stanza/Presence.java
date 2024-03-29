//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2015.12.11 at 11:34:34 PM MSK 
//


package com.expleague.xmpp.stanza;

import com.expleague.xmpp.AnyHolder;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;

import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.*;


/**
 * <p>Java class for anonymous complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;choice maxOccurs="unbounded" minOccurs="0">
 *           &lt;element ref="{jabber:client}show"/>
 *           &lt;element ref="{jabber:client}status"/>
 *           &lt;element ref="{jabber:client}priority"/>
 *         &lt;/choice>
 *         &lt;any processContents='lax' namespace='##other' maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element ref="{jabber:client}error" minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;attribute name="from" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="id" type="{http://www.w3.org/2001/XMLSchema}NMTOKEN" />
 *       &lt;attribute name="to" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="type">
 *         &lt;simpleType>
 *           &lt;restriction base="{http://www.w3.org/2001/XMLSchema}NMTOKEN">
 *             &lt;enumeration value="error"/>
 *             &lt;enumeration value="probe"/>
 *             &lt;enumeration value="subscribe"/>
 *             &lt;enumeration value="subscribed"/>
 *             &lt;enumeration value="unavailable"/>
 *             &lt;enumeration value="unsubscribe"/>
 *             &lt;enumeration value="unsubscribed"/>
 *           &lt;/restriction>
 *         &lt;/simpleType>
 *       &lt;/attribute>
 *       &lt;attribute ref="{http://www.w3.org/XML/1998/namespace}lang"/>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 *
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement
public class Presence extends Stanza implements AnyHolder {
  @XmlAttribute
  private PresenceType type;

  @XmlAnyElement(lax = true)
  protected List<Object> any;

  @XmlAttribute(name = "lang", namespace = "http://www.w3.org/XML/1998/namespace")
  @XmlSchemaType(name = "language")
  protected String lang;

  public Presence() {}

  public Presence(JID from, boolean available) {
    this.from = from;
    type = available ? PresenceType.AVAILABLE : PresenceType.UNAVAILABLE;
  }

  public Presence(JID from, boolean available, Item... contents) {
    this.from = from;
    type = available ? PresenceType.AVAILABLE : PresenceType.UNAVAILABLE;
    any = new ArrayList<>(Arrays.asList(contents));
  }

  public Presence(JID from, JID to, boolean available) {
    this.from = from;
    this.to = to;
    type = available ? PresenceType.AVAILABLE : PresenceType.UNAVAILABLE;
  }

  public boolean available() {
    return (type == null && status() == null) || type == PresenceType.AVAILABLE || "available".equals(status().value);
  }

  public Status status() {
    final Status status = get(Status.class);
    return status != null ? status : new Status(type == null ? PresenceType.AVAILABLE : type);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Presence)) return false;
    Presence presence = (Presence) o;

    return type == presence.type && (any == presence.any || (any != null && any.equals(presence.any)));
  }

  @Override
  public int hashCode() {
    int result = type != null ? type.hashCode() : 0;
    result = 31 * result + any.hashCode();
    return result;
  }

  @Override
  public List<? super Item> any() {
    return this.any != null ? this.any : (this.any = new ArrayList<>());
  }

  public <T extends Item> T copy(String idSuffix){
    final Presence clone = super.copy(idSuffix);
    if (any != null)
      clone.any = new ArrayList<>(any);
    //noinspection unchecked
    return (T)clone;
  }

  /**
   * <p>Java class for anonymous complex type.
   *
   * <p>The following schema fragment specifies the expected content contained within this class.
   *
   * <pre>
   * &lt;complexType>
   *   &lt;simpleContent>
   *     &lt;extension base="&lt;jabber:client>string1024">
   *       &lt;attribute ref="{http://www.w3.org/XML/1998/namespace}lang"/>
   *     &lt;/extension>
   *   &lt;/simpleContent>
   * &lt;/complexType>
   * </pre>
   *
   *
   */
  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlRootElement(name = "status")
  public static class Status extends Item {
    @XmlValue
    protected String value;
    @XmlAttribute(name = "lang", namespace = "http://www.w3.org/XML/1998/namespace")
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlSchemaType(name = "language")
    protected String lang;

    public Status() {}
    public Status(PresenceType type) {
      switch(type) {
        case UNAVAILABLE:
          value = "unavailable";
          break;
        case AVAILABLE:
        default:
          value = "available";
          break;
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Status)) return false;
      Status status = (Status) o;
      return value.equals(status.value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }
  }

  @SuppressWarnings("unused")
  @XmlRootElement
  public static class Show extends Item {
    @XmlValue
    private String value;

    public String value() {
      return value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Show)) return false;

      Show show = (Show) o;

      return value.equals(show.value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }
  }

  @SuppressWarnings("unused")
  @XmlRootElement
  public static class Priority extends Item {
    @XmlValue
    private Integer value;

    public int value() {
      return value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Priority)) return false;

      Priority priority = (Priority) o;

      return value.equals(priority.value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }
  }

  @XmlEnum
  public enum PresenceType {
    @XmlEnumValue(value = "error") ERROR,
    @XmlEnumValue(value = "probe") PROBE,
    @XmlEnumValue(value = "subscribe") SUBSCRIBE,
    @XmlEnumValue(value = "unsubscribe") UNSUBSCRIBE,
    @XmlEnumValue(value = "subscribed") SUBSCRIBED,
    @XmlEnumValue(value = "unsubscribed") UNSUBSCRIBED,
    @XmlEnumValue(value = "available") AVAILABLE,
    @XmlEnumValue(value = "unavailable") UNAVAILABLE,
  }
}
