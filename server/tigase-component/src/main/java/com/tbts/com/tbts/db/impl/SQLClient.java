package com.tbts.com.tbts.db.impl;

import com.tbts.model.Room;
import com.tbts.model.impl.ClientImpl;
import org.jetbrains.annotations.Nullable;

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
    synchronized (dao.updateClientState) {
      try {
        dao.updateClientState.setInt(1, state.index());
        dao.updateClientState.setString(2, id());
        dao.updateClientState.execute();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
    super.state(state);
  }

  @Override
  public void activate(@Nullable Room room) {
    synchronized (dao.updateClientActiveRoom) {
      try {
        dao.updateClientActiveRoom.setString(1, room != null ? room.id() : null);
        dao.updateClientActiveRoom.setString(2, id());
        dao.updateClientActiveRoom.execute();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
    super.activate(room);
  }

  public void update(Map<String, State> states, String currentActive) {
    // TODO
//    throw new NotImplementedException();
  }
}
