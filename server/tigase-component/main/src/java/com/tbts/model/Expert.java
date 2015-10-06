package com.tbts.model;

import com.spbsu.commons.func.WeakListenerHolder;
import tigase.xmpp.BareJID;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 18:49
 */
public interface Expert extends WeakListenerHolder<Expert> {
  BareJID id();

  void online(boolean val);

  boolean reserve(Room room);

  void invite();

  void ask(Room room);

  Room active();
  void free();


  enum State {
    READY,
    AWAY,
    CHECK,
    STEADY,
    INVITE,
    DENIED,
    CANCELED, GO
  }

  State state();
}
