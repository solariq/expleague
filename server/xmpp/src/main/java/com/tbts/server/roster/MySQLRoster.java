package com.tbts.server.roster;

import com.tbts.dao.MySQLOps;
import com.tbts.server.JabberUser;
import com.tbts.server.Roster;
import com.tbts.xmpp.control.register.Query;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: solar
 * Date: 11.12.15
 * Time: 22:37
 */
public class MySQLRoster extends MySQLOps implements Roster {
  public MySQLRoster(String connectionUrl) {
    super(connectionUrl);
  }

  @Override
  public Query required() {
    final Query query = new Query();
    query.name("");
    query.passwd("");
    return query;
  }

  @Override
  public void register(Query query) throws Exception {
    try (final PreparedStatement register = createStatement("register", "INSERT INTO tbts.Users SET id = ?, passwd = ?;")) {
      register.setString(1, query.name());
      register.setString(2, query.passwd());
      register.execute();
    }
  }

  @Override
  public synchronized JabberUser byName(String name) {
    try (final PreparedStatement byName = createStatement("byName", "SELECT * FROM tbts.Users WHERE id = ?;")) {
      byName.setString(1,  name);
      final ResultSet resultSet = byName.executeQuery();
      if (resultSet.next())
        return new JabberUser(name, resultSet.getString(2));
      else
        return null;
    }
    catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
