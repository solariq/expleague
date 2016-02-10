package com.expleague.xmpp.control.priv;

import com.expleague.xmpp.Item;
import com.expleague.xmpp.control.XMPPQuery;
import com.expleague.xmpp.stanza.Iq;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 18:51
 */
@XmlRootElement
public class Query extends XMPPQuery {
  @XmlElement(name = "storage", namespace = "storage:bookmarks")
  private String storage;

  @Override
  public Item reply(Iq.IqType type) {
    return this;
  }
}
