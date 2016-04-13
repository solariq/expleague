package com.expleague.model;

import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Experts League
 * Created by solar on 28/03/16.
 */
@SuppressWarnings("unused")
@XmlRootElement
public class Pattern extends Item {
  @XmlAttribute
  private String name;

  @XmlElement(namespace = Operations.NS)
  private String body;

  @XmlElement(namespace = Operations.NS)
  private String icon;

  public Pattern(String name, String body, String icon) {
    this.name = name;
    this.body = body;
    this.icon = icon;
  }

  public Pattern() {
  }

  public Pattern(String name, String icon) {
    this.name = name;
    this.icon = icon;
  }

  @Override
  public String toString() {
    return name;
  }

  public CharSequence body() {
    return body;
  }

  public String name() {
    return name;
  }

  public Pattern presentation() {
    return new Pattern(name, icon);
  }
}
