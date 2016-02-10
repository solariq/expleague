package com.expleague.xmpp.control.sasl;

import com.expleague.util.xml.Base64Adapter;
import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * User: solar
 * Date: 10.12.15
 * Time: 17:52
 */
@XmlRootElement
public class Challenge extends Item {
  @XmlValue
  @XmlJavaTypeAdapter(Base64Adapter.class)
  private byte[] challenge;

  public Challenge(byte[] challenge) {
    this.challenge = challenge;
  }

  public Challenge() {
  }

  public byte[] data() {
    return challenge;
  }
}
