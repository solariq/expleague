package com.expleague.server.services;

import akka.actor.UntypedActor;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.dao.Archive;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.util.akka.UntypedActorAdapter;
import com.expleague.util.ios.NotificationsManager;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.expleague.BestAnswerQuery;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Stanza;
import com.spbsu.commons.func.Functions;
import com.spbsu.commons.util.Holder;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Experts League
 * Created by solar on 14/04/16.
 */
public class BestAnswerService extends ActorAdapter<UntypedActor> {
  @ActorMethod
  public void invoke(Iq<BestAnswerQuery> rosterIq) {
    final JID requester = rosterIq.from();
    final LaborExchange.AnswerOfTheWeek aow = LaborExchange.board().answerOfTheWeek();
    if (aow == null) {
      sender().tell(Iq.error(rosterIq), self());
      return;
    }
    final String roomId = aow.roomId();
    final Archive.Dump dump = Archive.instance().dump(roomId);
    final JID owner = dump.owner();
    final List<Stanza> content = new ArrayList<>();
    final Holder<Offer> offerHolder = new Holder<>();
    dump.stream().flatMap(Functions.instancesOf(Message.class)).forEach(message -> {
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
    if (offerHolder.filled())
      sender().tell(Iq.answer(rosterIq, new BestAnswerQuery(offerHolder.getValue(), content)), self());
    else
      sender().tell(Iq.error(rosterIq), self());
  }
}
