package com.expleague.server.services;

import akka.actor.AbstractActor;
import akka.actor.UntypedActor;
import com.expleague.server.agents.ExpLeagueOrder;
import com.expleague.server.agents.LaborExchange;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.expleague.RestoreQuery;
import com.expleague.xmpp.stanza.Iq;

import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 14/04/16.
 */
public class RestoreService extends ActorAdapter<AbstractActor> {
  @ActorMethod
  public void invoke(Iq<RestoreQuery> restoreIq) {
    final RestoreQuery answer = restoreIq.get();
    final JID owner = restoreIq.from();
    try (final Stream<ExpLeagueOrder> related = LaborExchange.board().related(owner)) {
      related.filter(order -> order.role(owner) == ExpLeagueOrder.Role.OWNER)
          .forEach(order -> answer.addRoom(order.room().local()));
      sender().tell(Iq.answer(restoreIq, answer), self());
    }
  }
}
