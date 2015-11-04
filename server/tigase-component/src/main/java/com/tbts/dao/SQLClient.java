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

  public SQLClient(MySQLDAO dao, String id, boolean online, Map<String, State> state, String active) {
    super(id, online, state, active);
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
    final Room active = active();
    if (active != null) {
      final PreparedStatement updateRoomOwnerState = dao.getUpdateRoomOwnerState();
      synchronized (updateRoomOwnerState) {
        try {
          updateRoomOwnerState.setInt(1, state.index());
          updateRoomOwnerState.setString(2, active.id());
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
    final boolean available = dao.isUserAvailable(id());
    if (!available && this.online) {
      activate(null);
    }
    this.online = !available;
  }
}
