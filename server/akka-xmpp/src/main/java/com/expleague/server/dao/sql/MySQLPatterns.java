package com.expleague.server.dao.sql;

import com.expleague.model.Pattern;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.dao.PatternsRepository;
import com.spbsu.commons.io.StreamTools;

import java.io.IOException;
import java.sql.SQLException;
import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 28/03/16.
 */
@SuppressWarnings("unused")
public class MySQLPatterns extends MySQLOps implements PatternsRepository {
  public MySQLPatterns() {
    super(ExpLeagueServer.config().db());
  }

  @Override
  public Stream<Pattern> all() {
    return stream("all-patterns", "SELECT * FROM expleague.Patterns", stmt -> {}).map(rs -> {
      try {
        final String name = rs.getString(1);
        final String body = StreamTools.readReader(rs.getCharacterStream(2)).toString();
        final String icon = rs.getString(3);
        return new Pattern(name, body, icon);
      } catch (IOException | SQLException e) {
        throw new RuntimeException(e);
      }
    });
  }
}
