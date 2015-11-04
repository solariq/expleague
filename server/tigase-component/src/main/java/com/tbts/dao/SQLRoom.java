package com.tbts.dao;

import com.tbts.model.Client;
import com.tbts.model.Expert;
import com.tbts.model.handlers.ExpertManager;
import com.tbts.model.impl.RoomImpl;

import java.sql.SQLException;

/**
 * User: solar
 * Date: 26.10.15
 * Time: 18:40
 */
public class SQLRoom extends RoomImpl {
  private final MySQLDAO dao;
  private Expert active;

  public SQLRoom(MySQLDAO dao, String id, Client client, Expert active_expert, State state) {
    super(id, client);
    this.dao = dao;
    this.state = state;
    this.active = active_expert;
    if (active_expert != null)
      active_expert.addListener(workerListener);
  }

  public void update(State state, Expert active_expert) {
    if (state == State.DEPLOYED && active_expert == null && active == null)
      ExpertManager.instance().challenge(this);
    if (active_expert != active) {
      if (active != null)
        active.removeListener(workerListener);
      if (active_expert != null)
        active_expert.addListener(workerListener);
      active = active_expert;
    }
  }

  @Override
  public void enter(Expert winner) {
    synchronized (dao.updateRoomExpert) {
      try {
        dao.updateRoomExpert.setString(1, winner.id());
        dao.updateRoomExpert.setString(2, id());
        dao.updateRoomExpert.execute();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
    super.enter(winner);
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
