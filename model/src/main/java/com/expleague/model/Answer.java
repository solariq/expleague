package com.expleague.model;

import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.XmlAttribute;
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

  @XmlAttribute
  private Double difficulty;

  @XmlAttribute(name="extra-info")
  private Integer needMoreInfo;

  @XmlAttribute
  private Integer success;

  public Answer() {}

  public Answer(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public double difficulty() {
    return difficulty != null ? difficulty : 0;
  }

  public boolean specifications() {
    return needMoreInfo != null && needMoreInfo != 0;
  }

  public boolean success() {
    return success == null || success != 0;
  }
}
