package com.tbts.xmpp.control.sasl;

import com.tbts.xmpp.Item;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * User: solar
 * Date: 10.12.15
 * Time: 17:52
 */
@XmlRootElement
public class Auth extends Item {

  @XmlAttribute
  private String mechanism;

  public String mechanism() {
    return mechanism;
  }
}
