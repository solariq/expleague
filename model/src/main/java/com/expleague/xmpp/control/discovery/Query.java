package com.expleague.xmpp.control.discovery;

import com.expleague.xmpp.Item;
import com.expleague.xmpp.control.XMPPQuery;
import com.expleague.xmpp.stanza.Iq;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 18:49
 */
@XmlRootElement
public class Query extends XMPPQuery {
  @Override
  public Item reply(Iq.IqType type) {
    return this;
  }
}
