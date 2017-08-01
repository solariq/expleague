package com.expleague.server.admin.reports;

import akka.pattern.Patterns;
import akka.util.Timeout;
import com.expleague.model.*;
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
import com.spbsu.commons.util.Holder;
import org.apache.commons.lang.StringEscapeUtils;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
      headers("timestamp", "expert", "expert rating", "expert status", "topic", "continue", "room", "order", "tags", "patterns", "score", "score comment", "cancel/done", "active time", "suspend time", "deniers", "client");
      final Stream<ResultSet> mainResultStream;
      if (request.expertId() == null)
        mainResultStream = mySQLBoard.stream(
            "SELECT DISTINCT Participants.timestamp, Participants.partisipant, Users.trusted, Orders.offer, Orders.room, Participants.order, Orders.score, Participants.role FROM Participants INNER JOIN Orders ON Participants.order = Orders.id INNER JOIN Users ON Participants.partisipant = Users.id WHERE Participants.role < 7 AND Participants.timestamp >= ? AND Participants.timestamp <= ? GROUP BY Participants.partisipant, Participants.order ORDER BY Participants.timestamp",
            stmt -> {
              stmt.setTimestamp(1, new Timestamp(request.start()));
              stmt.setTimestamp(2, new Timestamp(request.end()));
            });
      else
        mainResultStream = mySQLBoard.stream(
            "SELECT DISTINCT Participants.timestamp, Participants.partisipant, Users.trusted, Orders.offer, Orders.room, Participants.order, Orders.score, Participants.role FROM Participants INNER JOIN Orders ON Participants.order = Orders.id INNER JOIN Users ON Participants.partisipant = Users.id WHERE Participants.role < 7 AND Participants.timestamp >= ? AND Participants.timestamp <= ? AND Participants.partisipant = ? GROUP BY Participants.partisipant, Participants.order ORDER BY Participants.timestamp",
            stmt -> {
              stmt.setTimestamp(1, new Timestamp(request.start()));
              stmt.setTimestamp(2, new Timestamp(request.end()));
              stmt.setString(3, request.expertId());
            });

      mainResultStream.forEach(resultSet -> {
        try {
          final Offer offer = Offer.create(resultSet.getString(4));
          if (offer == null || offer.client() == null)
            return;
          final String client = offer.client().local();
          final String topic = escape(offer.topic());

          final String expertId = resultSet.getString(2);
          final ExpertsProfile expertsProfile = Roster.instance().profile(expertId);
          final String rating = Double.toString(expertsProfile.rating());
          final String orderId = resultSet.getString(6);
          final String roomId = resultSet.getString(5);

          final JID jid = JID.parse(roomId + "@muc." + ExpLeagueServer.config().domain());
          final Timeout timeout = Timeout.apply(Duration.create(10, TimeUnit.MINUTES));
          final Future<Object> ask = Patterns.ask(XMPP.register(jid, context()), new RoomAgent.DumpRequest(), timeout);
          //noinspection unchecked
          final List<Stanza> dump = (List<Stanza>) Await.result(ask, timeout.duration());

          final ExpLeagueOrder.Role role = ExpLeagueOrder.Role.valueOf(resultSet.getInt(8));
          OrderResult orderResult = OrderResult.NA;
          { //order result
            if (role == ExpLeagueOrder.Role.DENIER)
              orderResult = OrderResult.CANCEL_FROM_INVITE;
            else if (role == ExpLeagueOrder.Role.SLACKER)
              orderResult = OrderResult.CANCEL_FROM_TASK;
            else if (role == ExpLeagueOrder.Role.ACTIVE) {
              boolean started = false;
              for (final Stanza stanza : dump) {
                if (!(stanza instanceof Message))
                  continue;
                final Message message = ((Message) stanza);
                if (message.has(Operations.Start.class)) {
                  final Operations.Start start = (message.get(Operations.Start.class));
                  if (orderId.equals(start.order())) {
                    started = true;
                    orderResult = OrderResult.IN_PROGRESS;
                  }
                  else if (started) {
                    if (orderResult != OrderResult.NEED_VERIFY)
                      orderResult = OrderResult.NA;
                    break;
                  }
                }
                else if (started && message.has(Operations.Cancel.class)) {
                  if (client.equals(message.from().local())) {
                    orderResult = OrderResult.CANCEL_BY_CLIENT;
                    break;
                  }
                  else if (expertId.equals(message.from().local())) {
                    orderResult = OrderResult.CANCEL_FROM_TASK;
                    break;
                  }
                }
                else if (started && message.has(Answer.class) && expertId.equals(message.from().local())) {
                  if (expertsProfile.authority().priority() <= ExpertsProfile.Authority.EXPERT.priority()) {
                    orderResult = OrderResult.DONE;
                    break;
                  }
                  else {
                    orderResult = OrderResult.NEED_VERIFY;
                  }
                }
                else if (started && message.has(Operations.Verified.class) && orderResult == OrderResult.NEED_VERIFY) {
                  orderResult = OrderResult.DONE;
                  break;
                }
                else if (started && message.has(Operations.Progress.class) && orderResult != OrderResult.NEED_VERIFY) {
                  final Operations.Progress progress = message.get(Operations.Progress.class);
                  if (orderId.equals(progress.order()) && progress.state() == OrderState.DONE) {
                    orderResult = OrderResult.NO_RESULT;
                    break;
                  }
                }
              }
            }
          }
          if (orderResult == OrderResult.NO_RESULT)
            return;

          String continueText = "";
          { //continue
            boolean prevAnswer = false;
            for (final Stanza stanza : dump) {
              if (!(stanza instanceof Message))
                continue;
              final Message message = ((Message) stanza);
              if (message.has(Answer.class) && expertsProfile.authority().priority() <= ExpertsProfile.Authority.EXPERT.priority()) {
                prevAnswer = true;
              }
              else if (message.has(Operations.Verified.class)) {
                prevAnswer = true;
              }
              else if (message.has(Message.Body.class)) {
                if (prevAnswer && client.equals(stanza.from().local())) {
                  continueText = escape(((Message) stanza).get(Message.Body.class).value());
                  prevAnswer = false;
                }
              }
              else if (message.has(Operations.Start.class)) {
                final Operations.Start start = ((Message) stanza).get(Operations.Start.class);
                if (start.order() != null && start.order().compareTo(orderId) >= 0) {
                  break;
                }
              }
            }
          }
          final StringBuilder patternsBuilder = new StringBuilder();
          { //patterns
            final Set<String> patterns = new HashSet<>(Arrays.stream(offer.patterns()).map(Pattern::name).collect(Collectors.toList()));
            for (final Stanza stanza : dump) {
              if (!(stanza instanceof Message))
                continue;
              final Message message = ((Message) stanza);
              if (message.has(Operations.Progress.class)) {
                final Operations.Progress progress = message.get(Operations.Progress.class);
                if (orderId.equals(progress.order())) {
                  progress.meta().forEach(metaChange -> {
                    if (metaChange.target() == Operations.Progress.MetaChange.Target.PATTERNS) {
                      if (metaChange.operation() == Operations.Progress.MetaChange.Operation.ADD) {
                        patterns.add(metaChange.name());
                      }
                      else {
                        patterns.remove(metaChange.name());
                      }
                    }
                  });
                }
              }
            }
            patterns.forEach(s -> patternsBuilder.append(s).append(";"));
          }

          final ExpLeagueOrder expLeagueOrder = LaborExchange.board().order(orderId);
          final StringBuilder tags = new StringBuilder();
          { //tags
            Arrays.stream(expLeagueOrder.tags()).forEach(tag -> tags.append(tag.name()).append(";"));
          }
          final String activeTime;
          final String suspendTime;
          { //time
            long activeTimeMs = 0;
            long suspendTimeMs = 0;
            final List<ExpLeagueOrder.StatusHistoryRecord> history = expLeagueOrder.statusHistoryRecords().collect(Collectors.toList());
            if (history.size() > 0) {
              final long fullTimeMs = history.get(history.size() - 1).getDate().getTime() - history.get(0).getDate().getTime();
              long startSuspended = -1;
              for (final ExpLeagueOrder.StatusHistoryRecord record : history) {
                if (record.getStatus() == OrderState.SUSPENDED) {
                  startSuspended = record.getDate().getTime();
                }
                else if (startSuspended != -1) {
                  suspendTimeMs += (record.getDate().getTime() - startSuspended);
                  startSuspended = -1;
                }
              }
              activeTimeMs = fullTimeMs - suspendTimeMs;
            }
            activeTime = String.format("%d h %d min", ((activeTimeMs / 1000) / 60 / 60), (((activeTimeMs / 1000) / 60)) % 60);
            suspendTime = String.format("%d h %d min", ((suspendTimeMs / 1000) / 60 / 60), (((suspendTimeMs / 1000) / 60)) % 60);
          }
          final String deniers = Long.toString(expLeagueOrder.of(ExpLeagueOrder.Role.DENIER).count() + expLeagueOrder.of(ExpLeagueOrder.Role.SLACKER).count());

          final double score = resultSet.getDouble(7);
          String scoreComment = "";
          { //score comment
            if (score >= 0) {
              final Holder<Message> feedbackHolder = new Holder<>();
              boolean started = false;
              boolean done = false;
              for (final Stanza stanza : dump) {
                if (!(stanza instanceof Message))
                  continue;
                final Message message = ((Message) stanza);
                if (message.has(Operations.Start.class)) {
                  final Operations.Start start = message.get(Operations.Start.class);
                  if (orderId.equals(start.order())) {
                    started = true;
                  }
                }
                else if (started && message.has(Answer.class) && expertId.equals(message.from().local())) {
                  done = true;
                }
                else if (message.has(Operations.Progress.class)) {
                  final Operations.Progress progress = message.get(Operations.Progress.class);
                  if (orderId.equals(progress.order()) && progress.state() == OrderState.DONE) {
                    done = true;
                  }
                }
                else if (done && message.has(Operations.Feedback.class)) {
                  feedbackHolder.setValue(message);
                  break;
                }
              }
              if (feedbackHolder.filled()) {
                final Message.Body scoreCommentBody = dump.stream().filter(stanza -> stanza instanceof Message).filter(stanza -> {
                  final Message message = (Message) stanza;
                  return message.ts() == feedbackHolder.getValue().ts() && message.has(Message.Body.class);
                }).map(stanza -> ((Message) stanza).get(Message.Body.class)).findFirst().orElse(null);
                if (scoreCommentBody != null) {
                  scoreComment = escape(scoreCommentBody.value());
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
              patternsBuilder.toString(),
              resultSet.getString(7),
              scoreComment,
              Integer.toString(orderResult.index()),
              activeTime,
              suspendTime,
              deniers,
              client
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

  private static String escape(String s) {
    return StringEscapeUtils.escapeCsv(s).replace("\n", "\\n");
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

  private enum OrderResult {
    CANCEL_FROM_INVITE(0),
    CANCEL_FROM_TASK(1),
    NEED_VERIFY(2),
    DONE(3),
    CANCEL_BY_CLIENT(4),
    IN_PROGRESS(5),
    NA(6),
    NO_RESULT(7),;

    int index;

    OrderResult(int index) {
      this.index = index;
    }

    public int index() {
      return index;
    }
  }
}
