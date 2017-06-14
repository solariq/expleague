package com.expleague.model;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;

/**
 * Experts League
 * Created by solar on 28.01.17.
 */

@XmlEnum
public enum Role {
  @XmlEnumValue("moderator")MODERATOR(0),
  @XmlEnumValue("participant")PARTICIPANT(1),
  @XmlEnumValue("visitor")VISITOR(2),
  @XmlEnumValue("none")NONE(3);

  private int priority;

  Role(int code) {
    this.priority = code;
  }

  public int priority() {
    return priority;
  }

  public static Role fromPriority(int priority) {
    for (Role type : Role.values()) {
      if (type.priority() == priority) {
        return type;
      }
    }
    return null;
  }
}
