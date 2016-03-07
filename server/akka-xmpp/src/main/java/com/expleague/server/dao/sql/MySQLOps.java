package com.expleague.server.dao.sql;

import org.intellij.lang.annotations.Language;

import java.sql.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * User: solar
 * Date: 11.11.15
 * Time: 18:55
 */
public class MySQLOps {
  private final String connectionUrl;
  private Connection conn;
  private final ThreadLocal<Map<String, PreparedStatement>> statements = new ThreadLocal<Map<String, PreparedStatement>>(){
    @Override
    protected Map<String, PreparedStatement> initialValue() {
      return new HashMap<>();
    }
  };

  public MySQLOps(String connectionUrl) {
    this.connectionUrl = connectionUrl;
  }

  public PreparedStatement createStatement(String name, @Language("MySQL") String stmt) {
    PreparedStatement preparedStatement = statements.get().get(name);
    try {
      if (preparedStatement == null || preparedStatement.isClosed() || preparedStatement.getConnection() == null) {
        preparedStatement = conn().prepareStatement(stmt);
      }
      preparedStatement.clearParameters();
      statements.get().put(name, preparedStatement);
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

  public Stream<ResultSet> stream(String name, @Language("MySQL") String stmt, QuerySetup setup) {
    try {
      final PreparedStatement statement = createStatement(name, stmt);
      setup.setup(statement);
      return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new ResultSetIterator(statement), 0), false);
    }
    catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public class ResultSetIterator implements Iterator<ResultSet> {
    private final PreparedStatement ps;
    private ResultSet rs;

    public ResultSetIterator(PreparedStatement ps) {
      this.ps = ps;
    }

    public void init() {
      try {
        rs = ps.executeQuery();
      } catch (SQLException e) {
        close();
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean hasNext() {
      if (ps == null)
        init();
      try {
        boolean hasMore = rs.next();
        if (!hasMore)
          close();
        return hasMore;
      }
      catch (SQLException e) {
        close();
        throw new RuntimeException(e);
      }
    }

    @Override
    public ResultSet next() {
      try {
        rs.next();
        return rs;
      }
      catch (SQLException e) {
        close();
        throw new RuntimeException(e);
      }
    }

    private void close() {
      try {
        rs.close();
      } catch (SQLException ignore) {
      }
    }
  }

  public interface QuerySetup {
    void setup(PreparedStatement stmt) throws SQLException;
  }

  static {
    try {
      Class.forName("com.mysql.jdbc.Driver");
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }


}
