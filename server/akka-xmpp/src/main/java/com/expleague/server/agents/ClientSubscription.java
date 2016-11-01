package com.expleague.server.agents;

import com.expleague.server.Subscription;
import com.expleague.xmpp.JID;

/**
 * Experts League
 * Created by solar on 01.11.16.
 */
public class ClientSubscription implements Subscription {
  private JID jid;

  public ClientSubscription(JID jid) {
    this.jid = jid;
  }

  @Override
  public JID who() {
    return jid;
  }

  @Override
  public boolean relevant(JID jid) {
    return jid.resource() != null && jid.resource().endsWith("expert");
  }
}

