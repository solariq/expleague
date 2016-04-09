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
    createStatement("drop-orders", "DELETE FROM Orders").execute();
    createStatement("drop-tags", "DELETE FROM Tags").execute();
    createStatement("drop-applications", "DELETE FROM Applications").execute();
    createStatement("drop-patterns", "DELETE FROM Patterns").execute();
    createStatement("drop-devices", "DELETE FROM Devices").execute();
    createStatement("drop-users", "DELETE FROM Users").execute();
  }
}
