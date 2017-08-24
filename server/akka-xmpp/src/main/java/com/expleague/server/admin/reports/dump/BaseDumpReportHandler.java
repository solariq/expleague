package com.expleague.server.admin.reports.dump;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.expleague.model.Offer;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.admin.reports.CsvReportHandler;
import com.expleague.server.agents.GlobalChatAgent;
import com.expleague.server.agents.RoomAgent;
import com.expleague.server.agents.XMPP;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Stanza;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * User: Artem
 * Date: 21.08.2017
 */
public abstract class BaseDumpReportHandler extends CsvReportHandler {
  private final SortedMap<String, String[]> sortedRows = new TreeMap<>();

  protected void startProcessing(long start, long end) {
    try {
      final Timeout timeout = new Timeout(Duration.create(30, TimeUnit.SECONDS));
      final Future<Object> roomsAsk = Patterns.ask(XMPP.register(XMPP.jid(GlobalChatAgent.ID), context()), new GlobalChatAgent.RoomsRequest(start), timeout);
      //noinspection unchecked
      final List<String> rooms = (List<String>) Await.result(roomsAsk, timeout.duration());
      for (final String roomId : rooms) {
        final JID roomJid = JID.parse(roomId + "@muc." + ExpLeagueServer.config().domain());
        final ActorRef roomActor = XMPP.register(roomJid, context());
        final Future<Object> dumpAsk = Patterns.ask(roomActor, new RoomAgent.DumpRequest(), timeout);
        //noinspection unchecked
        final List<Message> dump = ((List<Stanza>) Await.result(dumpAsk, timeout.duration()))
            .stream()
            .filter(stanza -> stanza instanceof Message)
            .map(stanza -> (Message) stanza)
            .collect(Collectors.toList());
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
    sortedRows.put(row[0], row);
  }

  @Override
  protected String build() {
    sortedRows.values().forEach(strings -> super.row(strings));
    return super.build();
  }
}
