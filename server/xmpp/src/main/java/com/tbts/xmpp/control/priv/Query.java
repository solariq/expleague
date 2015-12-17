package com.tbts.xmpp.control.priv;

import com.tbts.xmpp.Item;
import com.tbts.xmpp.control.XMPPQuery;
import com.tbts.xmpp.stanza.Iq;

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
  public Item reply(Iq.StanzaType type) {
    return this;
  }
}
