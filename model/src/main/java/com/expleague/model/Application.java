package com.expleague.model;

import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

/**
 * Experts League
 * Created by solar on 19/03/16.
 */
@XmlRootElement
public class Application extends Item {
  @XmlValue
  private String email;

  public String email() {
    return email;
  }
}
