package com.tbts.model.impl;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.model.Client;
import com.tbts.model.Reception;
import com.tbts.model.Room;
import tigase.xmpp.BareJID;

import java.util.HashSet;
import java.util.Set;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:31
 */
public class ClientImpl extends WeakListenerHolderImpl<Client> implements Client {
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

  protected Room active = null;
  public Room activate(BareJID roomId) {
    final Room room = active = Reception.instance().room(this, roomId);
    switch (room.state()) {
      case CLEAN:
        state(State.FORMULATING);
        break;
      case DEPLOYED:
      case LOCKED:
        state(State.COMMITED);
        break;
      case TIMEOUT:
        state(State.TIMEOUT);
        break;
      case COMPLETE:
        state(State.FEEDBACK);
        break;
      case CANCELED:
      case FIXED:
        state(State.ONLINE);
        break;
    }
    return room;
  }

  public void formulating() {
    if (state != State.ONLINE)
      throw new IllegalStateException();
    state(State.FORMULATING);
  }

  public void query() {
    if (state() != State.FORMULATING && state() != State.CHAT)
      throw new IllegalStateException();
    final Action<Room> lst = new Action<Room>() {
      @Override
      public void invoke(Room room) {
        if (room.state() == Room.State.COMPLETE)
          ClientImpl.this.feedback();
        pending.remove(this);
      }
    };
    active.addListener(lst);
    pending.add(lst);
    state(State.COMMITED);
  }

  protected final Set<Action<Room>> pending = new HashSet<>();
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
    invoke(this);
  }
}
