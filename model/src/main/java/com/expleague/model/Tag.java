package com.expleague.model;

import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

/**
 * Experts League
 * Created by solar on 13/03/16.
 */
@SuppressWarnings("unused")
@XmlRootElement
public class Tag extends Item {
  @XmlValue
  private String name;
  @XmlAttribute
  private Double score;

  @XmlAttribute
  private String icon;

  public Tag() {}

  public Tag(String name, double score) {
    this.name = name;
    this.score = score;
  }

  public Tag(String name) {
    this.name = name;
  }

  public Tag(String name, String icon) {
    this.name = name;
    this.icon = icon;
  }

  public double score() {
    return score != null ? score : 0.;
  }

  public String name() {
    return name;
  }

  public String icon() {
    return this.icon;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Tag tag = (Tag) o;

    return name.equals(tag.name);

  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }
}
