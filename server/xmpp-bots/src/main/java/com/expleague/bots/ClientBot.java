package com.expleague.bots;

import com.expleague.model.Offer;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;

/**
 * User: solar
 * Date: 11.10.15
 * Time: 21:10
 */
public class ClientBot extends Bot {
  public ClientBot(final BareJID jid, final String passwd) throws JaxmppException {
    super(jid, passwd, "client");
  }
}