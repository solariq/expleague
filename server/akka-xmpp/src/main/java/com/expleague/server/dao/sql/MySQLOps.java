package com.expleague.server.dao.sql;

import com.spbsu.commons.io.StreamTools;
import com.spbsu.commons.system.RuntimeUtils;
import org.intellij.lang.annotations.Language;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
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
      if (preparedStatement == null || preparedStatement.isClosed() || preparedStatement.getConnection() == null) {
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
      if (conn == null || conn.isClosed()) {
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
        if (rs != null)
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
