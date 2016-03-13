package com.expleague.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

/**
 * Experts League
 * Created by solar on 13/03/16.
 */
@SuppressWarnings("unused")
@XmlRootElement
public class Tag {
  @XmlValue
  private String name;
  @XmlAttribute
  private double score;

  public Tag() {}
  public Tag(String name, double score) {
    this.name = name;
    this.score = score;
  }

  public double score() {
    return score;
  }

  public String name() {
    return name;
  }
}
