package com.expleague.model;

import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Experts League
 * Created by solar on 01/03/16.
 */
@XmlRootElement
public class Delivered extends Item {
  @XmlAttribute
  private String id;
  @XmlAttribute
  private String resource;


  @SuppressWarnings("unused")
  public Delivered() {}

  public Delivered(String id, String resource) {
    this.id = id;
    this.resource = resource;
  }

  public String id() {
    return id;
  }

  public String resource() {
    return resource;
  }
}
