package com.expleague.server.roster;

import com.expleague.server.dao.MySQLOps;
import com.expleague.server.JabberUser;
import com.expleague.server.Roster;
import com.expleague.server.ExpLeagueServer;
import com.expleague.xmpp.control.register.Query;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 11.12.15
 * Time: 22:37
 */
public class MySQLRoster extends MySQLOps implements Roster {
  private static final Logger log = Logger.getLogger(MySQLRoster.class.getName());
  public MySQLRoster() {
    super(ExpLeagueServer.config().db());
  }

  @Override
  public Query required() {
    return Query.requiredFields();
  }

  @Override
  public void register(Query query) throws Exception {
    log.log(Level.FINE, "Registering user " + query.username());
    try (final PreparedStatement register = createStatement("register",
        "INSERT INTO tbts.Users SET id = ?, passwd = ?, country = ?, city = ?, realName = ?, avatarUrl = ?;")) {
      register.setString(1, query.username());
      register.setString(2, query.passwd());
      register.setString(3, query.country());
      register.setString(4, query.city());
      register.setString(5, query.name());
      register.setString(6, query.avatar());
      register.execute();
    }
  }

  @Override
  public synchronized JabberUser byName(String name) {
    try (final PreparedStatement byName = createStatement("byName", "SELECT * FROM tbts.Users WHERE id = ?;")) {
      byName.setString(1,  name);
      final ResultSet resultSet = byName.executeQuery();
      if (resultSet.next()) {

        return new JabberUser(
            name,
            resultSet.getString(2),
            resultSet.getString(3),
            resultSet.getString(4),
            resultSet.getString(6),
            resultSet.getString(5)
        );
      }
      else
        return null;
    }
    catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
