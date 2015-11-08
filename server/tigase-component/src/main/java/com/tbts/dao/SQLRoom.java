package com.tbts.dao;

import com.tbts.model.Client;
import com.tbts.model.Expert;
import com.tbts.model.handlers.ExpertManager;
import com.tbts.model.impl.RoomImpl;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * User: solar
 * Date: 26.10.15
 * Time: 18:40
 */
public class SQLRoom extends RoomImpl {
  private final MySQLDAO dao;

  public SQLRoom(MySQLDAO dao, String id, Client client) {
    super(id, client);
    this.dao = dao;
  }

  public void update(State state, Expert worker) {
    this.state = state;
    this.worker = worker;
    if (dao.isMaster(owner().id()) && state == State.DEPLOYED)
      ExpertManager.instance().challenge(this);
  }

  @Override
  public void enter(Expert winner) {
    if (!dao.isMaster(owner().id()))
      throw new IllegalStateException();

    final PreparedStatement updateRoomExpert = dao.getUpdateRoomExpert();
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (updateRoomExpert) {
      try {
        updateRoomExpert.setString(1, winner.id());
        updateRoomExpert.setString(2, id());
        updateRoomExpert.execute();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
    super.enter(winner);
  }

  @Override
  public void exit() {
    if (!dao.isMaster(owner().id()))
      throw new IllegalStateException();

    final PreparedStatement updateRoomExpert = dao.getUpdateRoomExpert();
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (updateRoomExpert) {
      try {
        updateRoomExpert.setString(1, null);
        updateRoomExpert.setString(2, id());
        updateRoomExpert.execute();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
    super.exit();
  }

  @Override
  public void state(State state) {
    if (!dao.isMaster(owner().id()))
      throw new IllegalStateException();
    final PreparedStatement updateRoomState = dao.getUpdateRoomState();
    //noinspection Duplicates,SynchronizationOnLocalVariableOrMethodParameter
    synchronized (updateRoomState) {
      try {
        updateRoomState.setInt(1, state.index());
        updateRoomState.setString(2, id());
        updateRoomState.execute();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
    super.state(state);
  }
}
