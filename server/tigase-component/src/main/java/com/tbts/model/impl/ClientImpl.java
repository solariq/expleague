package com.tbts.model.impl;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.model.Answer;
import com.tbts.model.Client;
import com.tbts.model.Room;
import com.tbts.model.handlers.ClientManager;
import com.tbts.model.handlers.ExpertManager;
import com.tbts.model.handlers.Reception;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:31
 */
public class ClientImpl extends WeakListenerHolderImpl<Client> implements Client {
  private final String id;
  private final Map<String, State> states = new HashMap<>();
  protected boolean online = false;
  private Action<Room> completeRoomListener = new Action<Room>() {
    @Override
    public void invoke(Room room) {
      if (room.state() == Room.State.COMPLETE) {
        room.removeListener(this);
        ClientImpl.this.feedback(room);
      }
    }
  };

  public ClientImpl(String id) {
    this(id, false, Collections.emptyMap(), null);
  }

  public ClientImpl(String id, boolean online, Map<String, State> states, @Nullable String active) {
    this.id = id;
    this.states.putAll(states);
    this.activeId = active;
    this.online = online;
    addListener(ClientManager.instance());
  }

  @Override
  public State state() {
    return (activeId != null && states.get(activeId) != null) ? states.get(activeId) : online ? State.ONLINE : State.OFFLINE;
  }

  @Override
  public String id() {
    return id;
  }

  @Nullable
  @Override
  public Room active() {
    return activeId != null ? Reception.instance().room(activeId) : null;
  }


  protected String activeId = null;
  public void activate(Room room) {
    if ((room == null && activeId == null) || (room != null && room.id().equals(activeId)))
      return;
    activeId = room != null ? room.id() : null;
  }

  public void formulating() {
    if (state() != State.ONLINE && state() == State.CHAT)
      throw new IllegalStateException();
    state(State.FORMULATING);
  }

  public void query() {
    if (state() != State.FORMULATING && state() != State.CHAT)
      throw new IllegalStateException();
    Reception.instance().room(activeId).addListener(completeRoomListener);
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
    if (val == online)
      return;
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
    else if (activeId == null) {
      throw new IllegalStateException();
    }
    else {
      states.put(activeId, state);
    }
    invoke(this);
  }
}
