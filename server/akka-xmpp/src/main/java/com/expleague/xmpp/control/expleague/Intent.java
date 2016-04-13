package com.expleague.xmpp.control.expleague;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;

/**
 * Experts League
 * Created by solar on 12/04/16.
 */
@XmlEnum
public enum Intent {
  @XmlEnumValue("presentation")PRESENTATION,
  @XmlEnumValue("work") WORK,
}
