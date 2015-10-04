package com.tbts.model.impl;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import tigase.xmpp.BareJID;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:13
 */
public abstract class ExpertImpl extends WeakListenerHolderImpl<Expert.State> implements Expert {
  private final BareJID id;

  private Action<Room.State> roomLst;
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
    else state(State.AWAY);
  }

  @Override
  public void reserve(Room room) {
    if (state != State.READY)
      throw new IllegalStateException();
    state(State.STEADY);
  }

  @Override
  public void free() {
    if (state == State.STEADY)
      state(State.READY);
  }

  @Override
  public void ask(Room room) {
    if (state != State.STEADY)
      throw new IllegalStateException();
    state(State.GO);
  }

  public void answer() {
    if (state != State.GO)
      throw new IllegalStateException();
    state(State.READY);
  }

  public void attach(Room room) {
    if (roomLst != null)
      throw new IllegalStateException("Expert must be in detached state before receiving new requests!");
    room.addListener(roomLst = state -> {
      switch (state) {
        case CLEAN:
          break;
        case DEPLOYED:
          break;
        case CHALLENGE:
          break;
        case LOCKED:
          break;
        case TIMEOUT:
          break;
        case COMPLETE:
          break;
        case FIXED:
          break;
      }
    });
    state = State.CHECK;
  }

  public boolean detach() {
    boolean result = roomLst != null;
    roomLst = null;
    return result;
  }

  protected void state(State state) {
    this.state = state;
    invoke(state);
  }

  @Override
  public State state() {
    return state;
  }
}
