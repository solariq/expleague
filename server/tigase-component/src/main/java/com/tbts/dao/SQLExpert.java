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

  public SQLExpert(MySQLDAO dao, String id) {
    super(id);
    this.dao = dao;
  }

  @Override
  protected void state(State state) {
    final PreparedStatement updateExpertState = dao.getUpdateExpertState();
    synchronized (updateExpertState) {
      try {
        updateExpertState.setInt(1, state.index());
        updateExpertState.setString(2, active);
        updateExpertState.setString(3, id());
        updateExpertState.execute();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
    super.state(state);
  }

  public void update(State state, String active) {
    if (dao.isUserAvailable(id())) {
      this.active = active;
      this.state = state;
    }
    else {
      this.active = null;
      this.state = State.AWAY;
    }
  }
}
