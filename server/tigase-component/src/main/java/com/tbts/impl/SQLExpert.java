package com.tbts.impl;

import com.tbts.model.impl.ExpertImpl;

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
    synchronized (dao.updateExpertState) {
      try {
        dao.updateExpertState.setInt(1, state.index());
        dao.updateExpertState.setString(2, id());
        dao.updateExpertState.execute();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
    super.state(state);
  }

  public void update(State state, String active) {
    // TODO
//    throw new NotImplementedException();
  }
}
