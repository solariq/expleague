package com.expleague.model;

import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.*;

/**
 * User: Artem
 * Date: 14.07.2017
 */
@XmlRootElement
public class Social extends Item {
  @XmlAttribute
  private SocialType type;
  @XmlValue
  private String id;

  public Social() {}

  public Social(SocialType type, String id) {
    this.type = type;
    this.id = id;
  }

  public String id() {
    return id;
  }

  public SocialType type() {
    return type;
  }
}
