package com.tbts.model.impl;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.model.Answer;
import com.tbts.model.Client;
import com.tbts.model.Room;
import com.tbts.model.experts.ExpertManager;
import tigase.xmpp.BareJID;

import java.util.*;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:31
 */
public class ClientImpl extends WeakListenerHolderImpl<Client> implements Client {
  private final BareJID id;
  private final Map<Room, State> states = new HashMap<>();
  boolean online = false;
  private Action<Room> completeRoomListener = new Action<Room>() {
    @Override
    public void invoke(Room room) {
      if (room.state() == Room.State.COMPLETE) {
        room.removeListener(this);
        ClientImpl.this.feedback(room);
      }
    }
  };

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
    if (state() != State.ONLINE && state() == State.CHAT)
      throw new IllegalStateException();
    state(State.FORMULATING);
  }

  public void query() {
    if (state() != State.FORMULATING && state() != State.CHAT)
      throw new IllegalStateException();
    active.addListener(completeRoomListener);
    state(State.COMMITED);
  }

  public void feedback(Room room) {
    final Room active = active();
    activate(room);
    if (state() != State.COMMITED && state() != State.CHAT)
      throw new IllegalStateException();
    final List<Answer> answers = room.answers();
    if (answers.size() < 1 || answers.get(answers.size() - 1) == Answer.EMPTY)
      ExpertManager.instance().challenge(room);
    else
      state(State.FEEDBACK);

    activate(active);
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
