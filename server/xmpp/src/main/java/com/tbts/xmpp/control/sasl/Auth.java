package com.tbts.xmpp.control.sasl;

import com.tbts.util.xml.Base64Adapter;
import com.tbts.xmpp.Item;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * User: solar
 * Date: 10.12.15
 * Time: 17:52
 */
@XmlRootElement
public class Auth extends Item {

  @XmlAttribute
  private String mechanism;

  @XmlValue
  @XmlJavaTypeAdapter(Base64Adapter.class)
  private byte[] challenge;

  public byte[] challenge() {
    return challenge;
  }

  public String mechanism() {
    return mechanism;
  }
}
