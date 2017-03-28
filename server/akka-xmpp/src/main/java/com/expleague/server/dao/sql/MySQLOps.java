package com.expleague.server.dao.sql;

import com.spbsu.commons.util.ThreadTools;
import org.intellij.lang.annotations.Language;

import java.sql.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Spliterators;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * User: solar
 * Date: 11.11.15
 * Time: 18:55
 */
public class MySQLOps {
  private static final Logger log = Logger.getLogger(MySQLOps.class.getName());

  public static final int ATTEMPT_TIMEOUT_MS = 1000;
  public static final int MAX_NUMBER_OF_ATTEMPTS = 20;

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
    return createStatement(name, stmt, false);
  }

  public PreparedStatement createStatement(String name, @Language("MySQL") String stmt, boolean returnGenKeys) {
    PreparedStatement preparedStatement = statements.get().get(name);
    try {
      int attempt = 0;
      while (preparedStatement == null || preparedStatement.isClosed() || preparedStatement.getConnection() == null || preparedStatement.getConnection() != conn || conn.isClosed() || !conn.isValid(0)) {
        if (attempt++ > MAX_NUMBER_OF_ATTEMPTS) {
          throw new RuntimeException("Unable to prepareStatement in " + MAX_NUMBER_OF_ATTEMPTS + " attempts");
        }
        ThreadTools.sleep(attempt * ATTEMPT_TIMEOUT_MS);
        preparedStatement = conn().prepareStatement(stmt, returnGenKeys ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS);
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

  public Stream<ResultSet> stream(String name, @Language("MySQL") String stmt, QuerySetup setup) {
    try {
      final PreparedStatement statement = createStatement(name, stmt);
      if (setup != null)
        setup.setup(statement);
      final Stream<ResultSet> stream = StreamSupport.stream(Spliterators.spliteratorUnknownSize(new ResultSetIterator(statement), 0), false);
      return stream.onClose(() -> {
        try {
          statement.close();
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      });
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
      }
      catch (SQLException e) {
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
      }
      catch (SQLException e) {
        close();
        throw new RuntimeException(e);
      }
    }

    @Override
    public ResultSet next() {
      return rs;
    }

    private void close() {
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
