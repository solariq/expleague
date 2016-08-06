package com.expleague.server.services;

import akka.actor.UntypedActor;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.Roster;
import com.expleague.server.XMPPUser;
import com.expleague.server.agents.ExpLeagueOrder;
import com.expleague.server.agents.ExpLeagueRoomAgent;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.agents.XMPP;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.expleague.BestAnswerQuery;
import com.expleague.xmpp.control.expleague.RestoreQuery;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Stanza;
import com.spbsu.commons.func.Functions;
import com.spbsu.commons.util.Holder;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 14/04/16.
 */
public class RestoreService extends ActorAdapter<UntypedActor> {
  @ActorMethod
  public void invoke(Iq<RestoreQuery> restoreIq) {
    final RestoreQuery answer = restoreIq.get();
    final JID owner = restoreIq.from();
    LaborExchange.board().related(owner)
        .filter(order -> order.role(owner) == ExpLeagueOrder.Role.OWNER)
        .forEach(order -> answer.addRoom(order.room().local()));
    sender().tell(Iq.answer(restoreIq, answer), self());
  }
}
