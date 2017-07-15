package com.expleague.model;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import java.util.stream.Stream;

/**
 * User: Artem
 * Date: 14.07.2017
 */
@XmlEnum
public enum SocialType {
  @XmlEnumValue(value = "facebook")FACEBOOK(0);

  int index;

  SocialType(int index) {
    this.index = index;
  }

  public int code() {
    return index;
  }

  public static SocialType valueOf(int index) {
    return Stream.of(SocialType.values()).filter(s -> s.index == index).findAny().orElse(null);
  }
}
