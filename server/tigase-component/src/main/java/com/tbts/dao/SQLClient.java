package com.tbts.dao;

import com.tbts.model.Room;
import com.tbts.model.impl.ClientImpl;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

/**
 * User: solar
 * Date: 26.10.15
 * Time: 18:27
 */
class SQLClient extends ClientImpl {
  private MySQLDAO dao;

  public SQLClient(MySQLDAO dao, String id) {
    super(id);
    this.dao = dao;
  }

  @Override
  protected void state(State state) {
    final PreparedStatement updateClientState = dao.getUpdateClientState();
    synchronized (updateClientState) {
      try {
        updateClientState.setInt(1, state.index());
        updateClientState.setString(2, id());
        updateClientState.execute();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
    if (activeId != null) {
      final PreparedStatement updateRoomOwnerState = dao.getUpdateRoomOwnerState();
      synchronized (updateRoomOwnerState) {
        try {
          updateRoomOwnerState.setInt(1, state.index());
          updateRoomOwnerState.setString(2, activeId);
          updateRoomOwnerState.execute();
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      }
    }
    super.state(state);
  }

  @Override
  public void activate(@Nullable Room room) {
    final PreparedStatement updateClientActiveRoom = dao.getUpdateClientActiveRoom();
    synchronized (updateClientActiveRoom) {
      try {
        updateClientActiveRoom.setString(1, room != null ? room.id() : null);
        updateClientActiveRoom.setString(2, id());
        updateClientActiveRoom.execute();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
    super.activate(room);
  }

  public void update(Map<String, State> states, String currentActive) {
    this.stateInRooms.putAll(states);
    this.activeId = currentActive;
    final boolean available = dao.isUserAvailable(id());
    if (!available) {
      state(State.OFFLINE);
    }
    else if (activeId != null){
      State state = stateInRooms.get(activeId);
      this.state = state != null ? state : State.ONLINE;
    }
    else {
      this.state = State.ONLINE;
    }
  }
}
