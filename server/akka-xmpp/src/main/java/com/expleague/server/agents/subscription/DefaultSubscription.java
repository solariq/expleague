package com.expleague.server.agents.subscription;

import com.expleague.server.Subscription;
import com.expleague.xmpp.JID;

/**
 * Experts League
 * Created by solar on 13/04/16.
 */
public class DefaultSubscription implements Subscription {
  private JID jid;

  public DefaultSubscription(JID jid) {
    this.jid = jid;
  }

  @Override
  public JID who() {
    return jid;
  }

  @Override
  public boolean relevant(JID jid) {
    return !jid.bareEq(this.jid);
  }
}
