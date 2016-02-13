package com.expleague.model;

import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

/**
 * Experts League
 * Created by solar on 13/02/16.
 */
@XmlRootElement
public class Answer extends Item {
  @XmlValue
  private String value;

  public Answer() {}

  public Answer(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
