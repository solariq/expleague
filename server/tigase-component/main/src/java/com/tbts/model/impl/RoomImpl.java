package com.tbts.model.impl;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.spbsu.commons.util.Holder;
import com.tbts.experts.ExpertManager;
import com.tbts.model.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:25
 */
public class RoomImpl extends WeakListenerHolderImpl<Room.State> implements Room {
  @SuppressWarnings({"FieldCanBeLocal", "unused"})
  private final Action<Client.State> clientLst;
  private final String id;

  private State state;

  public RoomImpl(String id, Client client) {
    this.id = id;
    state = State.CLEAN;
    client.addListener(clientLst = state -> {
      switch (state) {
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
    challenge();
  }

  @Override
  public String id() {
    return id;
  }

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final List<Action<Expert.State>> challenge = new ArrayList<>();
  protected void challenge() {
    if (!challenge.isEmpty())
      throw new IllegalStateException("Previous challenge has not finished!");
    final Iterator<Expert> available = ExpertManager.instance().available(this);
    final Holder<Expert> winner = new Holder<>();
    while (available.hasNext()) {
      final Expert next = available.next();
      final Action<Expert.State> check = new Action<Expert.State>() {
        boolean once = false;
        @Override
        public void invoke(Expert.State state) {
          if (once)
            return;
          once = true;
          if (state == Expert.State.STEADY) {
            synchronized (winner) {
              if (winner.filled()) {
                next.free();
                return;
              }
              winner.setValue(next);
            }
            RoomImpl.this.state(State.LOCKED);
            challenge.clear();
            next.ask(RoomImpl.this);
          }
        }
      };
      next.addListener(check);
      challenge.add(check);
      next.reserve(this);
    }
  }

  @Override
  public void query(Query text) {
  }

  private void state(State state) {
    this.state = state;
    invoke(state);
  }

  @Override
  public void answer(Answer answer) {
    state(State.COMPLETE);
  }

  @Override
  public State state() {
    return state;
  }
}
