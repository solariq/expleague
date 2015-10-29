package com.tbts.dao;

import com.tbts.model.Client;
import com.tbts.model.Expert;
import com.tbts.model.impl.RoomImpl;

import java.sql.SQLException;

/**
 * User: solar
 * Date: 26.10.15
 * Time: 18:40
 */
public class SQLRoom extends RoomImpl {
  private final MySQLDAO dao;

  public SQLRoom(MySQLDAO dao, String id, Client client, Expert active_expert, State state) {
    super(id, client);
    this.dao = dao;
  }

  public void update(State state, Expert active_expert) {
    // TODO
  }

  @Override
  protected void state(State state) {
    synchronized (dao.updateRoomState) {
      try {
        dao.updateRoomState.setInt(1, state.index());
        dao.updateRoomState.setString(2, id());
        dao.updateRoomState.execute();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
    super.state(state);
  }
}
