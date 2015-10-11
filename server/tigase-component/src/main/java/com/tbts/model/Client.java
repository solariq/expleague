package com.tbts.model;

import com.spbsu.commons.func.WeakListenerHolder;
import tigase.xmpp.BareJID;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 18:46
 */
public interface Client extends WeakListenerHolder<Client> {
  BareJID id();

  Room active();
  void activate(Room room);
  void formulating();
  void query();
  void presence(boolean val);

  enum State {
    OFFLINE,
    ONLINE,
    FORMULATING,
    COMMITED,
    TIMEOUT,
    FEEDBACK,
    CHAT
  }

  State state();
}
