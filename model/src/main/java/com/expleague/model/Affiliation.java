package com.expleague.model;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;

/**
 * Experts League
 * Created by solar on 25.12.16.
 */
@XmlEnum
public enum Affiliation {
  @XmlEnumValue("owner") OWNER(0),
  @XmlEnumValue("admin") ADMIN(1),
  @XmlEnumValue("member") MEMBER(2),
  @XmlEnumValue("visitor") VISITOR(3),
  @XmlEnumValue("none") NONE(4),
  @XmlEnumValue("outcast") OUTCAST(5);

  private int priority;
  Affiliation(int priority) {
    this.priority = priority;
  }

  public int priority() { return priority; }

  public static Affiliation fromPriority(int priority) {
    for (Affiliation type : Affiliation.values()) {
      if (type.priority() == priority) {
        return type;
      }
    }
    return null;
  }
}
