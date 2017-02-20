package com.expleague.server.agents.subscription;

import com.expleague.server.Subscription;
import com.expleague.server.XMPPDevice;
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
    if (jid.local() == null || jid.local().isEmpty())
      return true;
    final XMPPDevice device = XMPPDevice.fromJid(jid);
    return device != null && device.expert();
  }
}

