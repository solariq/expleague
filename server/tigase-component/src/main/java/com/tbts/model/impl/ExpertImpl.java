package com.tbts.model.impl;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.model.Answer;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import com.tbts.model.handlers.ExpertManager;
import com.tbts.model.handlers.Reception;
import org.jetbrains.annotations.Nullable;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:13
 */
public class ExpertImpl extends WeakListenerHolderImpl<Expert> implements Expert {
  private final String id;
  protected State state;

  public ExpertImpl(String id) {
    this.id = id;
    state = State.AWAY;
    addListener(ExpertManager.instance());
  }

  @Override
  public String id() {
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
        Reception.instance().room(active).answer(null);
      join(null);
      state(State.AWAY);
    }
  }

  protected volatile String active;
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
      if (room.id().equals(active) && room.state() == Room.State.CANCELED) {
        state(State.CANCELED);
        join(null);
      }
    }
  };

  private void join(@Nullable Room room) {
    if ((active == null && room == null) || (room != null && room.id().equals(active)))
      return;
    if (active != null) {
      final Room activeRoom = Reception.instance().room(active);
      activeRoom.removeListener(activeRoomListener);
      activeRoom.exit(this);
    }
    if (room != null)
      room.addListener(activeRoomListener);
    active = room != null ? room.id() : null;
  }

  @Override
  public void free() {
    if (state == State.STEADY || state == State.INVITE)
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
  @Nullable
  public Room active() {
    return active != null ? Reception.instance().room(active) : null;
  }

  public void answer(Answer answer) {
    if (state != State.GO || active == null)
      throw new IllegalStateException();
    final Room room = Reception.instance().room(active);
    join(null);
    room.answer(answer);
    state(State.READY);
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
