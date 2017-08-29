package com.expleague.server.admin.reports.dump;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.expleague.model.Offer;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.admin.reports.CsvReportHandler;
import com.expleague.server.agents.GlobalChatAgent;
import com.expleague.server.agents.XMPP;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import org.jetbrains.annotations.NotNull;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * User: Artem
 * Date: 21.08.2017
 */
public abstract class BaseDumpReportHandler extends CsvReportHandler {
  private final List<Row> rows = new ArrayList<>();

  protected void startProcessing(long start, long end) {
    try {
      final Timeout timeout = new Timeout(Duration.create(30, TimeUnit.SECONDS));
      final Future<Object> roomsAsk = Patterns.ask(XMPP.register(XMPP.jid(GlobalChatAgent.ID), context()), new GlobalChatAgent.RoomsRequest(start), timeout);
      //noinspection unchecked
      final List<String> rooms = (List<String>) Await.result(roomsAsk, timeout.duration());
      for (final String roomId : rooms) {
        final JID roomJid = JID.parse(roomId + "@muc." + ExpLeagueServer.config().domain());
        final ActorRef roomDumpActor = context().actorOf(RoomDumpAgent.props(roomJid));
        final Future<Object> dumpAsk = Patterns.ask(roomDumpActor, new RoomDumpAgent.DumpRequest(), timeout);
        //noinspection unchecked
        final List<Message> dump = ((List<Message>) Await.result(dumpAsk, timeout.duration()));
        context().stop(roomDumpActor);

        final long initTs = dump.stream()
            .filter(message -> message.has(Offer.class))
            .map(message -> message.get(Offer.class))
            .map(offer -> (long) (offer.started() * 1000))
            .findAny().orElse(0L);
        if (initTs > end)
          continue;
        process(roomId, dump);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected abstract void process(String roomId, List<Message> dump);

  @Override
  protected void row(String... row) {
    rows.add(new Row(row[0], row));
  }

  @Override
  protected String build() {
    Collections.sort(rows);
    rows.forEach(row -> super.row(row.values()));
    return super.build();
  }

  private static class Row implements Comparable<Row> {
    private final String key;
    private final String[] values;

    private Row(String key, String[] values) {
      this.key = key;
      this.values = values;
    }

    public String key() {
      return key;
    }

    public String[] values() {
      return values;
    }

    @Override
    public int compareTo(@NotNull Row o) {
      return key.compareTo(o.key());
    }
  }
}
