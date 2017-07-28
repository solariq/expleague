package com.expleague.server.admin.reports;

import akka.pattern.Patterns;
import akka.util.Timeout;
import com.expleague.model.Answer;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.Roster;
import com.expleague.server.agents.ExpLeagueOrder;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.agents.RoomAgent;
import com.expleague.server.agents.XMPP;
import com.expleague.server.dao.sql.MySQLBoard;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Stanza;
import org.apache.commons.lang.StringEscapeUtils;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

    final MySQLBoard mySQLBoard = (MySQLBoard) board;
    try {
      headers("timestamp", "expert", "expert rating", "expert status", "topic", "continue", "room", "order", "tags", "score");
      final Stream<ResultSet> mainResultStream;
      if (request.expertId() == null)
        mainResultStream = mySQLBoard.stream(
            "SELECT DISTINCT Participants.timestamp, Participants.partisipant, Users.trusted, Orders.offer, Orders.room, Participants.order, Orders.score, Participants.role FROM Participants INNER JOIN Orders ON Participants.order = Orders.id INNER JOIN Users ON Participants.partisipant = Users.id WHERE Participants.role < 7 AND Participants.timestamp >= ? AND Participants.timestamp <= ? ORDER BY Participants.timestamp",
            stmt -> {
              stmt.setTimestamp(1, new Timestamp(request.start()));
              stmt.setTimestamp(2, new Timestamp(request.end()));
            });
      else
        mainResultStream = mySQLBoard.stream(
            "SELECT DISTINCT Participants.timestamp, Participants.partisipant, Users.trusted, Orders.offer, Orders.room, Participants.order, Orders.score, Participants.role FROM Participants INNER JOIN Orders ON Participants.order = Orders.id INNER JOIN Users ON Participants.partisipant = Users.id WHERE Participants.role < 7 AND Participants.timestamp >= ? AND Participants.timestamp <= ? AND Participants.partisipant = ? ORDER BY Participants.timestamp",
            stmt -> {
              stmt.setTimestamp(1, new Timestamp(request.start()));
              stmt.setTimestamp(2, new Timestamp(request.end()));
              stmt.setString(3, request.expertId());
            });

      mainResultStream.forEach(resultSet -> {
        try {
          final String expertId = resultSet.getString(2);
          final String rating = Double.toString(Roster.instance().profile(expertId).rating());
          //noinspection ConstantConditions
          final String topic = StringEscapeUtils.escapeCsv(((Offer) Offer.create(resultSet.getString(4))).topic()).replace("\n", "\\n");
          final String orderId = resultSet.getString(6);
          final StringBuilder tags = new StringBuilder();
          { //tags
            try (final Stream<ResultSet> tagsStream = mySQLBoard.stream("SELECT Tags.tag FROM Tags INNER JOIN Topics ON Tags.id = Topics.tag WHERE Topics.order = ?",
                stmt -> stmt.setString(1, orderId))) {
              tagsStream.forEach(r -> {
                try {
                  tags.append(r.getString(1)).append(";");
                } catch (SQLException e) {
                  throw new RuntimeException(e);
                }
              });
            }
          }

          final String roomId = resultSet.getString(5);
          final JID jid = JID.parse(roomId + "@muc." + ExpLeagueServer.config().domain());
          final Timeout timeout = Timeout.apply(Duration.create(10, TimeUnit.MINUTES));
          final Future<Object> ask = Patterns.ask(XMPP.register(jid, context()), new RoomAgent.DumpRequest(), timeout);
          //noinspection unchecked
          final List<Stanza> dump = (List<Stanza>) Await.result(ask, timeout.duration());
          String continueText = "";
          { //continue
            final String owner;
            try (final PreparedStatement getOwner = mySQLBoard.createStatement("SELECT partisipant FROM Participants WHERE `order` = ? AND role = ?")) {
              getOwner.setString(1, orderId);
              getOwner.setInt(2, ExpLeagueOrder.Role.OWNER.index());
              try (final ResultSet getOwnerResult = getOwner.executeQuery()) {
                owner = getOwnerResult.next() ? getOwnerResult.getString(1) : null;
              }
            }
            if (owner != null) {
              boolean prevAnswer = false;
              for (final Stanza stanza : dump) {
                if (stanza instanceof Message && ((Message) stanza).has(Answer.class)) {
                  prevAnswer = true;
                }
                else if (stanza instanceof Message && ((Message) stanza).has(Message.Body.class)) {
                  if (prevAnswer && stanza.from().local().equals(owner)) {
                    continueText = StringEscapeUtils.escapeCsv(((Message) stanza).get(Message.Body.class).value()).replace("\n", "\\n");
                    prevAnswer = false;
                  }
                }
                else if (stanza instanceof Message && ((Message) stanza).has(Operations.Start.class)) {
                  final Operations.Start start = ((Message) stanza).get(Operations.Start.class);
                  if (start.order().compareTo(orderId) >= 0) {
                    break;
                  }
                }
              }
            }
          }
          row(
              resultSet.getString(1),
              expertId,
              rating,
              resultSet.getString(3),
              topic,
              continueText,
              roomId,
              orderId,
              tags.toString(),
              resultSet.getString(7)
          );
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
      mainResultStream.close();
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
