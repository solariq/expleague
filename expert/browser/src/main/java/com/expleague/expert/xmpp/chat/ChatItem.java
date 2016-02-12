package com.expleague.expert.xmpp.chat;

import com.expleague.xmpp.Item;

/**
 * Experts League
 * Created by solar on 11/02/16.
 */
public interface ChatItem {
  boolean outgoing();
  Item toMessage();
}
