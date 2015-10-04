package com.tbts.model;

import com.spbsu.commons.func.WeakListenerHolder;
import tigase.xmpp.BareJID;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 18:46
 */
public interface Client extends WeakListenerHolder<Client.State> {
  BareJID id();

  void dialogue();
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
