package com.expleague.server.dao.sql;

import com.expleague.util.stream.RequiresClose;
import com.spbsu.commons.util.ThreadTools;
import org.intellij.lang.annotations.Language;

import java.sql.*;
import java.util.Iterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * User: solar
 * Date: 11.11.15
 * Time: 18:55
 */
public class MySQLOps {
  public static final int ATTEMPT_TIMEOUT_MS = 1000;
  public static final int MAX_NUMBER_OF_ATTEMPTS = 20;

  private final String connectionUrl;
  private Connection conn;

  public MySQLOps(String connectionUrl) {
    this.connectionUrl = connectionUrl;
  }

  public PreparedStatement createStatement(@Language("MySQL") String stmt) throws SQLException {
    return createStatement(stmt, false);
  }

  public PreparedStatement createStatement(@Language("MySQL") String stmt, boolean returnGenKeys) throws SQLException {
    return conn().prepareStatement(stmt, returnGenKeys ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS);
  }

  public Connection conn() {
    try {
      int attempt = 0;
      while (conn == null || conn.isClosed() || !conn.isValid(0)) {
        if (attempt++ > MAX_NUMBER_OF_ATTEMPTS) {
          throw new RuntimeException("Unable to get connection in " + MAX_NUMBER_OF_ATTEMPTS + " attempts");
        }
        ThreadTools.sleep(attempt * ATTEMPT_TIMEOUT_MS);
        conn = DriverManager.getConnection(connectionUrl);
      }
      return conn;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @RequiresClose
  public Stream<ResultSet> stream(@Language("MySQL") String stmt, QuerySetup setup) throws SQLException {
    final PreparedStatement statement = createStatement(stmt);
    final ResultSetIterator resultSetIterator = new ResultSetIterator(statement);
    try {
      if (setup != null)
        setup.setup(statement);
      final Stream<ResultSet> stream = StreamSupport.stream(Spliterators.spliteratorUnknownSize(resultSetIterator, 0), false);
      return stream.onClose(() -> {
        try {
          resultSetIterator.close();
          statement.close();
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      });
    } catch (Error | RuntimeException e) {
      try {
        resultSetIterator.close();
        statement.close();
      } catch (SQLException ex) {
        try {
          e.addSuppressed(ex);
        } catch (Throwable ignore) {
        }
      }
      throw e;
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
      if (rs == null)
        init();
      try {
        if (rs.isClosed())
          return false;
        boolean hasMore = rs.next();
        if (!hasMore)
          close();
        return hasMore;
      } catch (SQLException e) {
        close();
        throw new RuntimeException(e);
      }
    }

    @Override
    public ResultSet next() {
      return rs;
    }

    public void close() {
      try {
        if (rs != null && !rs.isClosed())
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
      //noinspection ResultOfMethodCallIgnored
//      final Process exec = Runtime.getRuntime().exec("/bin/bash");
//      final PrintStream bash = new PrintStream(exec.getOutputStream());
//      bash.println("mysql -u root --password=tg30239");
//      bash.append(StreamTools.readStream(MySQLOps.class.getResourceAsStream("/tbts-schema.sql")));
//      exec.getOutputStream().close();
//      exec.waitFor();
//      final String info = StreamTools.readStream(exec.getInputStream()).toString();
//      if (!info.isEmpty())
//        log.info(info);
//      final String warn = StreamTools.readStream(exec.getErrorStream()).toString();
//      if (!warn.isEmpty())
//        log.warning(warn);

    } catch (ClassNotFoundException /*| InterruptedException | IOException*/ e) {
      throw new RuntimeException(e);
    }
  }


}
