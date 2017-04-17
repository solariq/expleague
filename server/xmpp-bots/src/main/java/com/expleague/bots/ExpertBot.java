package com.expleague.bots;

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;

/**
 * User: solar
 * Date: 11.10.15
 * Time: 21:10
 */
public class ExpertBot extends Bot {

  public ExpertBot(final BareJID jid, final String passwd) {
    super(jid, passwd, "expert", "/expert");
  }

  protected ExpertBot(final BareJID jid, final String passwd, String resource, String email) {
    super(jid, passwd, resource, email);
  }
}
