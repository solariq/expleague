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
import com.expleague.xmpp.stanza.data.Err;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.*;
import java.util.Collections;
import java.util.List;


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
 *         &lt;any processContents='lax' namespace='##other' minOccurs="0"/>
 *         &lt;element ref="{jabber:client}error" minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;attribute name="from" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="id" use="required" type="{http://www.w3.org/2001/XMLSchema}NMTOKEN" />
 *       &lt;attribute name="to" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="type" use="required">
 *         &lt;simpleType>
 *           &lt;restriction base="{http://www.w3.org/2001/XMLSchema}NMTOKEN">
 *             &lt;enumeration value="error"/>
 *             &lt;enumeration value="get"/>
 *             &lt;enumeration value="result"/>
 *             &lt;enumeration value="set"/>
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
@XmlType(name = "", propOrder = {
    "any",
    "error"
})
@XmlRootElement(name = "iq")
public class Iq<T> extends Stanza {

  public static <T> Iq<T> answer(Iq<?> request, T content) {
    final Iq<T> result = new Iq<>(request.id, IqType.RESULT, content);
    result.from = request.to;
    result.to = request.from;
    return result;
  }

  public static Iq<Void> answer(Iq<?> request) {
    return answer(request, null);
  }

  public static <T extends Item> Iq<T> create(JID to, JID from, IqType type, T item) {
    final Iq<T> iq = new Iq<>();
    iq.from(from);
    iq.any = item;
    iq.to(to);
    iq.type = type;
    return iq;
  }

  public static <T> Iq<T> error(Iq<T> request) {
    final Iq<T> result = new Iq<>(request.id, IqType.ERROR, null);
    result.from = request.to;
    result.to = request.from;
    return result;
  }

  @XmlAnyElement(lax = true)
  protected T any;
  @XmlElementRef
  protected Err error;
  @XmlAttribute
  private IqType type;

  public Iq(){}

  private Iq(String id, IqType type, T content) {
    super(id);
    this.type = type;
    if (content instanceof Err)
      this.error = (Err) content;
    else if (content != null)
      this.any = content;
  }

  public T get() {
    try {
      //noinspection RedundantCast
      return (T) any;
    }
    catch (ClassCastException cce) {
      return null;
    }
  }

  @Nullable
  public String serviceNS() {
    if (any instanceof Item) {
      return Item.ns((Item)any);
    }
    return null;
  }

  public Iq error(Err error) {
    this.error = error;
    return this;
  }

  public IqType type() {
    return type;
  }

  @XmlEnum
  public enum IqType {
    @XmlEnumValue(value = "error") ERROR,
    @XmlEnumValue(value = "get") GET,
    @XmlEnumValue(value = "set") SET,
    @XmlEnumValue(value = "result") RESULT,
  }
}
