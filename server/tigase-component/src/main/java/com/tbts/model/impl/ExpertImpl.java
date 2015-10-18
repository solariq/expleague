package com.tbts.model.impl;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.model.Answer;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import tigase.xmpp.BareJID;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:13
 */
public class ExpertImpl extends WeakListenerHolderImpl<Expert> implements Expert {
  private final BareJID id;
  private State state;

  public ExpertImpl(BareJID id) {
    this.id = id;
    state = State.AWAY;
  }

  @Override
  public BareJID id() {
    return id;
  }

  @Override
  public void online(boolean val) {
    if (val) {
      if (state == State.AWAY)
        state(State.READY);
    }
    else {
      if (active != null)
        active.answer(null);
      join(null);
      state(State.AWAY);
    }
  }

  private volatile Room active;
  @Override
  public boolean reserve(Room room) {
    if (state != State.READY || active != null)
      return false;
    join(room);
    state(State.CHECK);
    return true;
  }

  private final Action<Room> activeRoomListener = new Action<Room>() {
    @Override
    public void invoke(Room room) {
      if (active == room && room.state() == Room.State.CANCELED) {
        state(State.CANCELED);
        join(null);
      }
    }
  };

  private void join(Room room) {
    if (active != null)
      active.removeListener(activeRoomListener);
    if (room != null)
      room.addListener(activeRoomListener);
    active = room;
  }

  @Override
  public void free() {
    if (state == State.STEADY)
      state(State.READY);
    join(null);
  }

  @Override
  public void steady() {
    if (state != State.CHECK)
      throw new IllegalStateException();
    state(State.STEADY);
  }

  @Override
  public void invite() {
    if (state != State.STEADY)
      throw new IllegalStateException();
    state(State.INVITE);
  }

  @Override
  public void ask(Room room) {
    if (state != State.INVITE || active == null)
      throw new IllegalStateException();
    state(State.GO);
  }

  @Override
  public Room active() {
    return active;
  }

  public void answer(Answer answer) {
    if (state != State.GO || this.active == null)
      throw new IllegalStateException();
    state(State.READY);
    final Room room = this.active;
    join(null);
    room.answer(answer);
  }

  protected void state(State state) {
    this.state = state;
    invoke(this);
  }

  @Override
  public State state() {
    return state;
  }
}
