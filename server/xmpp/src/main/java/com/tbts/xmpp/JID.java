package com.tbts.xmpp;

import javax.xml.bind.annotation.XmlValue;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * User: solar
 * Date: 07.12.15
 * Time: 13:01
 */
public class JID {
  @XmlValue
  private String addr;

  @SuppressWarnings("unused")
  public JID() {}

  public JID(String addr) {
    this.addr = addr;
    URI.create(addr);
  }

  public URI asURI() {
    try {
      return new URI(addr);
    }
    catch (URISyntaxException ignore) {
      throw new RuntimeException(ignore);
    }
  }

  public JID bare() {
    final int resourceStart = addr.indexOf('/');
    if (resourceStart >= 0)
      return new JID(addr.substring(0, resourceStart));
    return this;
  }

  public String addr() {
    return addr;
  }

  public boolean hasResource() {
    return addr.indexOf('/') >= 0;
  }
}
