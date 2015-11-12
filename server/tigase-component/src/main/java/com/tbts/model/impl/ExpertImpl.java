package com.tbts.model.impl;

import com.tbts.model.Expert;
import com.tbts.model.Room;
import com.tbts.model.StateWise;
import com.tbts.model.handlers.ExpertManager;
import com.tbts.model.handlers.Reception;
import org.jetbrains.annotations.Nullable;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:13
 */
public class ExpertImpl extends StateWise.Stub<Expert.State, Expert> implements Expert {
  private final String id;

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
    else if (state != State.AWAY) {
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

  private void join(@Nullable Room room) {
    if ((active == null && room == null) ||
        (room != null && room.id().equals(active)))
      return;
    if (active != null) {
      final Room activeRoom = Reception.instance().room(active);
      activeRoom.exit();
    }
    active = room != null ? room.id() : null;
  }

  @Override
  public void free() {
    if (state != State.STEADY && state != State.INVITE && state != State.GO)
      throw new IllegalStateException(state.toString());
    join(null);
    state(State.READY);
  }

  @Override
  public void steady() {
    if (state != State.CHECK)
      throw new IllegalStateException(state.toString());
    state(State.STEADY);
  }

  @Override
  public void invite() {
    if (state != State.STEADY)
      throw new IllegalStateException(state.toString());
    state(State.INVITE);
  }

  @Override
  public void ask() {
    if (state != State.INVITE || active == null)
      throw new IllegalStateException(state.toString());
    state(State.GO);
  }

  @Override
  @Nullable
  public Room active() {
    return active != null ? Reception.instance().room(active) : null;
  }
}
