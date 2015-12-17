package com.tbts.xmpp.control.discovery;

import com.tbts.xmpp.Item;
import com.tbts.xmpp.control.XMPPQuery;
import com.tbts.xmpp.stanza.Iq;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 18:49
 */
@XmlRootElement
public class Query extends XMPPQuery {
  @Override
  public Item reply(Iq.StanzaType type) {
    return this;
  }
}
