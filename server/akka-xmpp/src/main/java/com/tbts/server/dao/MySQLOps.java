package com.tbts.server.dao;

import org.intellij.lang.annotations.Language;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: solar
 * Date: 11.11.15
 * Time: 18:55
 */
public class MySQLOps {
  private final String connectionUrl;
  private Connection conn;
  private final Map<String, PreparedStatement> statements = new ConcurrentHashMap<>();

  public MySQLOps(String connectionUrl) {
    this.connectionUrl = connectionUrl;
  }

  public PreparedStatement createStatement(String name, @Language("MySQL") String stmt) {
    PreparedStatement preparedStatement = statements.get(name);
    try {
      if (preparedStatement == null || preparedStatement.isClosed() || preparedStatement.getConnection() == null) {
        preparedStatement = conn().prepareStatement(stmt);
      }
      preparedStatement.clearParameters();
      statements.put(name, preparedStatement);
      return preparedStatement;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }

  }

  public Connection conn() {
    try {
      if (conn == null || conn.isClosed())
        conn = DriverManager.getConnection(connectionUrl);
      return conn;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  static {
    try {
      Class.forName("com.mysql.jdbc.Driver");
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
