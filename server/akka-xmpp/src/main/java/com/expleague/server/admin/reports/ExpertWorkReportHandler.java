package com.expleague.server.admin.reports;

import com.expleague.server.agents.LaborExchange;
import com.expleague.server.dao.sql.MySQLBoard;
import com.expleague.util.akka.ActorMethod;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.stream.Stream;

/**
 * User: Artem
 * Date: 24.07.2017
 */
public class ExpertWorkReportHandler extends CsvReportHandler {
  @ActorMethod
  public void report(ReportRequest request) {
    final LaborExchange.Board board = LaborExchange.board();
    if (!(board instanceof MySQLBoard)) {
      sender().tell("MySQLBoard is disabled", self());
      return;
    }

    try {
      headers("timestamp", "expert", "status", "room", "order", "score");
      final Stream<ResultSet> resultStream;
      if (request.expertId() == null)
        resultStream = ((MySQLBoard) board).stream(
            "SELECT DISTINCT Participants.timestamp, Participants.partisipant, Users.trusted, Orders.offer, Orders.room, Participants.order, Orders.score, Participants.role FROM Participants INNER JOIN Orders ON Participants.order = Orders.id INNER JOIN Users ON Participants.partisipant = Users.id WHERE Participants.role < 7 AND Participants.timestamp >= ? AND Participants.timestamp <= ? ORDER BY Participants.timestamp",
            stmt -> {
              stmt.setTimestamp(1, new Timestamp(request.start()));
              stmt.setTimestamp(2, new Timestamp(request.end()));
            });
      else
        resultStream = ((MySQLBoard) board).stream(
            "SELECT DISTINCT Participants.timestamp, Participants.partisipant, Users.trusted, Orders.offer, Orders.room, Participants.order, Orders.score, Participants.role FROM Participants INNER JOIN Orders ON Participants.order = Orders.id INNER JOIN Users ON Participants.partisipant = Users.id WHERE Participants.role < 7 AND Participants.timestamp >= ? AND Participants.timestamp <= ? AND Participants.partisipant = ? ORDER BY Participants.timestamp",
            stmt -> {
              stmt.setTimestamp(1, new Timestamp(request.start()));
              stmt.setTimestamp(2, new Timestamp(request.end()));
              stmt.setString(3, request.expertId());
            });

      resultStream.forEach(resultSet -> {
        try {
          row(
              resultSet.getString(1),
              resultSet.getString(2),
              resultSet.getString(3),
              resultSet.getString(5),
              resultSet.getString(6),
              resultSet.getString(7)
          );
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      });
      resultStream.close();
      sender().tell(build(), self());
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public static class ReportRequest {
    private final long start;
    private final long end;
    private final String expertId;


    public ReportRequest(long start, long end, String expertId) {
      this.start = start;
      this.end = end;
      this.expertId = expertId;
    }

    long start() {
      return start;
    }

    long end() {
      return end;
    }

    String expertId() {
      return expertId;
    }
  }
}
