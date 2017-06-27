package com.expleague.server.dao.sql;

import com.expleague.server.ExpLeagueServer;

/**
 * @author vpdelta
 */
public class MySQLTestOps extends MySQLOps {
  public MySQLTestOps() {
    super(ExpLeagueServer.config().db());
  }

  public void setUp() throws Exception {
    createStatement("DELETE FROM Orders").execute();
    createStatement("DELETE FROM Tags").execute();
    createStatement("DELETE FROM Applications").execute();
    createStatement("DELETE FROM Patterns").execute();
    createStatement("DELETE FROM Devices").execute();
    createStatement("DELETE FROM Users").execute();
  }
}
