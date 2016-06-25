package com.expleague.server.services;

import akka.actor.UntypedActor;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.agents.ExpLeagueRoomAgent;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.agents.XMPP;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.expleague.BestAnswerQuery;
import com.expleague.xmpp.control.expleague.DumpRoomQuery;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Stanza;
import com.spbsu.commons.func.Functions;
import com.spbsu.commons.util.Holder;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 14/04/16.
 */
public class DumpRoomService extends ActorAdapter<UntypedActor> {
  private static final Logger log = Logger.getLogger(DumpRoomService.class.getName());
  @ActorMethod
  public void invoke(Iq<DumpRoomQuery> rosterIq) {
    final String roomId = rosterIq.get().room();
    if (roomId == null) {
      sender().tell(Iq.error(rosterIq), self());
      return;
    }
    final Timeout timeout = new Timeout(Duration.create(2, TimeUnit.SECONDS));
    final JID roomJid = new JID(roomId, "muc." + ExpLeagueServer.config().domain(), null);
    final Future<Object> ask = Patterns.ask(XMPP.register(roomJid, context()), new ExpLeagueRoomAgent.DumpRequest(), timeout);
    try {
      //noinspection unchecked
      final List<Stanza> result = (List<Stanza>) Await.result(ask, timeout.duration());
      final List<Stanza> content = new ArrayList<>();
      final Holder<Offer> offerHolder = new Holder<>();
      result.stream().flatMap(Functions.instancesOf(Message.class)).forEach(message -> {
        if (message.has(Offer.class) && !offerHolder.filled()) {
          final Offer offer = message.get(Offer.class);
          offer.room(roomJid);
          offerHolder.setValue(offer);
        }
        else
          content.add(message);
      });
      if (offerHolder.filled())
        sender().tell(Iq.answer(rosterIq, new DumpRoomQuery(offerHolder.getValue(), content)), self());
      else
        sender().tell(Iq.error(rosterIq), self());
    }
    catch (Exception e) {
      log.log(Level.WARNING, "Unable to receive answer of the week room dump!", e);

    }
  }
}
