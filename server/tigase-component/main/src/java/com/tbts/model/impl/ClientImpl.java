package com.tbts.model.impl;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.model.Client;
import com.tbts.model.Reception;
import com.tbts.model.Room;
import tigase.xmpp.BareJID;

import java.util.ArrayList;
import java.util.List;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:31
 */
public abstract class ClientImpl extends WeakListenerHolderImpl<Client.State> implements Client {
  private final BareJID id;
  private State state;
  private State savedState;

  public ClientImpl(BareJID id) {
    this.id = id;
    this.savedState = State.ONLINE;
    this.state = State.OFFLINE;
  }

  @Override
  public State state() {
    return state;
  }

  @Override
  public BareJID id() {
    return id;
  }

  protected Room allocated = null;
  public void dialogue() {
    if (state != State.ONLINE)
      throw new IllegalStateException();
    allocated = Reception.instance().create(this);
    state(State.FORMULATING);
  }

  public void query() {
    if (state() != State.FORMULATING)
      throw new IllegalStateException();
    final Action<Room.State> lst = state -> {
      if (state == Room.State.COMPLETE)
        feedback();
    };
    allocated.addListener(lst);
    pending.add(lst);
    state(State.COMMITED);
  }

  protected final List<Action<Room.State>> pending = new ArrayList<>();
  public void feedback() {
    if (state != State.COMMITED && state != State.CHAT)
      throw new IllegalStateException();
    state(State.FEEDBACK);
  }

  public void presence(boolean val) {
    if (!val) {
      savedState = state;
      state(State.OFFLINE);
    }
    else state(savedState);
  }

  protected void state(State state) {
    this.state = state;
    invoke(state);
  }
}
