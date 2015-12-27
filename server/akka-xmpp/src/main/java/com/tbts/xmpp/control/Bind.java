package com.tbts.xmpp.control;

import com.tbts.xmpp.JID;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 16:56
 */
@XmlRootElement(namespace = Bind.NS)
public class Bind extends XMPPFeature {
  public static final String NS = "urn:ietf:params:xml:ns:xmpp-bind";
  @XmlElement(namespace = NS)
  @Nullable
  private JID jid;

  @XmlElement
  @Nullable
  String resource;

  public Bind() {}

  public Bind(JID jid) {
    this.jid = jid;
  }

  public String resource() {
    return resource;
  }
}
