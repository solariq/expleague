package com.expleague.model;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 16.03.17.
 */
@XmlEnum
public enum OrderState {
  @XmlEnumValue("none") NONE(-1),
  @XmlEnumValue("open") OPEN(0),
  @XmlEnumValue("in-progress") IN_PROGRESS(1),
  @XmlEnumValue("suspended") SUSPENDED(2),
  @XmlEnumValue("done") DONE(3),;

  int index;

  OrderState(int index) {
    this.index = index;
  }

  public int code() {
    return index;
  }

  public static OrderState valueOf(int index) {
    return Stream.of(OrderState.values()).filter(s -> s.index == index).findAny().orElse(null);
  }
}
