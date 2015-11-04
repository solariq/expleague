package com.tbts.dao;

import com.tbts.model.impl.ExpertImpl;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * User: solar
 * Date: 26.10.15
 * Time: 18:28
 */
public class SQLExpert extends ExpertImpl {
  private MySQLDAO dao;

  public SQLExpert(MySQLDAO dao, String id, State state, String active) {
    super(id);
    this.dao = dao;
    this.state = state;
    this.active = active;
  }

  @Override
  protected void state(State state) {
    final PreparedStatement updateExpertState = dao.getUpdateExpertState();
    synchronized (updateExpertState) {
      try {
        updateExpertState.setInt(1, state.index());
        updateExpertState.setString(2, id());
        updateExpertState.execute();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
    super.state(state);
  }

  public void update(State state, String active) {
    if (!dao.isUserAvailable(id())) {
      this.state = State.AWAY;
    }
    else
      this.state = state;
  }
}
