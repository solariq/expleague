package com.expleague.server;

import com.expleague.xmpp.JID;

/**
 * Experts League
 * Created by solar on 13/04/16.
 */
public interface Subscription {
  JID who();

  boolean relevant(JID jid);
}
