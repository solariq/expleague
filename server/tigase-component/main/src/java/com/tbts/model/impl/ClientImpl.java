package com.tbts.model.impl;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.model.Client;
import com.tbts.model.Room;
import tigase.xmpp.BareJID;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:31
 */
public class ClientImpl extends WeakListenerHolderImpl<Client> implements Client {
  private final BareJID id;
  private final Map<Room, State> states = new HashMap<>();
  boolean online = false;

  public ClientImpl(BareJID id) {
    this.id = id;
  }

  @Override
  public State state() {
    return (active != null && states.get(active) != null) ? states.get(active) : online ? State.ONLINE : State.OFFLINE;
  }

  @Override
  public BareJID id() {
    return id;
  }

  @Override
  public Room active() {
    return active;
  }


  protected Room active = null;
  public void activate(Room room) {
    if (active == room)
      return;
    active = room;
  }

  public void formulating() {
    if (state() != State.ONLINE)
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
    if (state() != State.COMMITED && state() != State.CHAT)
      throw new IllegalStateException();
    state(State.FEEDBACK);
  }

  public void presence(boolean val) {
    state(val ? State.ONLINE : State.OFFLINE);
  }

  protected void state(State state) {
    if (state() == state)
      return;

    if (state == State.ONLINE) {
      online = true;
      activate(null);
    }
    else if (state == State.OFFLINE) {
      online = false;
      activate(null);
    }
    else if (active == null) {
      throw new IllegalStateException();
    }
    else {
      states.put(active, state);
    }
    invoke(this);
  }
}
