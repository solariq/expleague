package com.expleague.server.services;

import akka.actor.UntypedActor;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.XMPPDevice;
import com.expleague.server.agents.ExpLeagueRoomAgent;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.agents.XMPP;
import com.expleague.server.notifications.NotificationsManager;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.expleague.BestAnswerQuery;
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
public class BestAnswerService extends ActorAdapter<UntypedActor> {
  private static final Logger log = Logger.getLogger(BestAnswerService.class.getName());
  @ActorMethod
  public void invoke(Iq<BestAnswerQuery> rosterIq) {
    final JID requester = rosterIq.from();
    final LaborExchange.AnswerOfTheWeek aow = LaborExchange.board().answerOfTheWeek();
    if (aow == null) {
      sender().tell(Iq.error(rosterIq), self());
      return;
    }
    final String roomId = aow.roomId();
    final Timeout timeout = new Timeout(Duration.create(2, TimeUnit.SECONDS));
    final Future<Object> ask = Patterns.ask(XMPP.register(new JID(roomId, "muc." + ExpLeagueServer.config().domain(), null), context()), new ExpLeagueRoomAgent.DumpRequest(), timeout);
    try {
      //noinspection unchecked
      final List<Stanza> result = (List<Stanza>) Await.result(ask, timeout.duration());
      final List<Stanza> content = new ArrayList<>();
      final Holder<Offer> offerHolder = new Holder<>();
      final JID owner = result.get(0).from();
      result.stream().flatMap(Functions.instancesOf(Message.class)).forEach(message -> {
        if (message.from().bareEq(owner)) {
          final Message copy = message.copy();
          if (copy.has(Offer.class) && !offerHolder.filled()) {
            final Offer offer = copy.get(Offer.class);
            offer.client(requester);
            offer.room(JID.parse(roomId + "-copy-" + requester.local() + "@muc." + ExpLeagueServer.config().domain()));
            offerHolder.setValue(offer);
          }
          else if (!copy.has(Operations.Command.class)){
            copy.from(requester);
            content.add(copy);
          }
        }
        else if (message.to().bareEq(owner) || message.type() == Message.MessageType.GROUP_CHAT) {
          final Stanza copy = message.copy();
          copy.to(requester);
          content.add(copy);
        }
      });
      if (offerHolder.filled()) {
        sender().tell(Iq.answer(rosterIq, new BestAnswerQuery(offerHolder.getValue(), content)), self());
        NotificationsManager.delivered(roomId, XMPPDevice.fromJid(rosterIq.from()), context());
      }
      else
        sender().tell(Iq.error(rosterIq), self());
    }
    catch (Exception e) {
      log.log(Level.WARNING, "Unable to receive answer of the week room dump!", e);

    }
  }
}
