package com.tbts.model.impl;

import com.tbts.model.Client;
import com.tbts.model.Room;
import com.tbts.model.StateWise;
import com.tbts.model.handlers.ClientManager;
import com.tbts.model.handlers.Reception;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:31
 */
public class ClientImpl extends StateWise.Stub<Client.State, Client> implements Client {
  private final String id;
  protected final Map<String, State> stateInRooms = new HashMap<>();
  protected boolean online = false;

  public ClientImpl(String id) {
    this.id = id;
    this.state = State.OFFLINE;
    addListener(ClientManager.instance());
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
    State state = stateInRooms.get(activeId);
    if (state == null)
      state(this.state != State.OFFLINE ? State.ONLINE : State.OFFLINE);
    else
      this.state = state;
  }

  public void formulating() {
    if (state() != State.ONLINE && state() == State.CHAT || active() == null)
      throw new IllegalStateException();
    state(State.FORMULATING);
  }

  public void query() {
    if (state() != State.FORMULATING && state() != State.CHAT)
      throw new IllegalStateException();
    state(State.COMMITED);
    final Room room = Reception.instance().room(activeId);
    room.commit();
  }

  public void feedback(Room room) {
    if (state(room) != State.COMMITED && state(room) != State.CHAT)
      throw new IllegalStateException();
    activate(room);
    state(State.FEEDBACK);
  }

  public State state(Room room) {
    return stateInRooms.get(room.id());
  }

  public void online(boolean val) {
    if (val == online)
      return;
    state(val ? State.ONLINE : State.OFFLINE);
  }

  @Override
  protected void state(State state) {
    if (state == State.ONLINE || state == State.OFFLINE) {
      online = state == State.ONLINE;
    }
    else if (activeId == null) {
      throw new IllegalStateException();
    }
    else {
      stateInRooms.put(activeId, state);
    }
    super.state(state);
  }
}
