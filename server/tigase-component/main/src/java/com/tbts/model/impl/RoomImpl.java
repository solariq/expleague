package com.tbts.model.impl;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.model.*;
import com.tbts.model.experts.ExpertManager;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:25
 */
public class RoomImpl extends WeakListenerHolderImpl<Room> implements Room {
  @SuppressWarnings({"FieldCanBeLocal", "unused"})
  private final Action<Client> clientLst;
  private final String id;

  private State state;

  public RoomImpl(String id, Client client) {
    this.id = id;
    state = State.CLEAN;
    client.addListener(clientLst = cl -> {
      switch (cl.state()) {
        case CHAT:
        case COMMITED:
          commit();
          break;
        case FEEDBACK:
          fix();
          break;
      }
    });
  }

  protected void fix() {
    if (state != State.COMPLETE)
      throw new IllegalStateException();
  }

  protected void commit() {
    if (state != State.CLEAN && state != State.COMPLETE)
      throw new IllegalStateException();
    state(State.DEPLOYED);
    ExpertManager.instance().challenge(this);
  }

  @Override
  public String id() {
    return id;
  }

  private Query.Builder qBuilder = new Query.Builder();

  @Override
  public void text(String text) {
    if (state != State.CLEAN)
      throw new IllegalStateException();
    qBuilder.addText(text);
  }

  public Query query() {
    return qBuilder.build();
  }

  private void state(State state) {
    this.state = state;
    invoke(this);
  }

  @Override
  public void answer(Answer answer) {
    state(State.COMPLETE);
  }

  @Override
  public void enterExpert(Expert winner) {
    state(State.LOCKED);
    winner.invite();
  }

  @Override
  public State state() {
    return state;
  }
}
