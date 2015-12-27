package com.tbts.xmpp.control.sasl;

import com.tbts.util.xml.Base64Adapter;
import com.tbts.xmpp.Item;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * User: solar
 * Date: 10.12.15
 * Time: 17:52
 */
@XmlRootElement
public class Response extends Item {
  @XmlValue
  @XmlJavaTypeAdapter(Base64Adapter.class)
  private byte[] data;

  public Response(byte[] challenge) {
    this.data = challenge;
  }

  public Response() {
  }

  public byte[] data() {
    return data;
  }
}
