package com.expleague.util.xml;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.util.Base64;

/**
 * User: solar
 * Date: 10.12.15
 * Time: 19:07
 */
public class Base64Adapter extends XmlAdapter<String, byte[]> {
  @Override
  public byte[] unmarshal(String v) throws Exception {
    return Base64.getDecoder().decode(v);
  }

  @Override
  public String marshal(byte[] v) throws Exception {
    return Base64.getEncoder().encodeToString(v);
  }
}
