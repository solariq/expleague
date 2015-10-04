package com.tbts.model;

import com.spbsu.commons.func.WeakListenerHolder;
import tigase.xmpp.BareJID;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 18:49
 */
public interface Expert extends WeakListenerHolder<Expert.State> {
  BareJID id();

  void online(boolean val);

  void reserve(Room room);
  void free();
  void ask(Room room);


  enum State {
    READY,
    AWAY,
    CHECK,
    STEADY,
    INVITE,
    DENIED,
    GO
  }

  State state();
}
