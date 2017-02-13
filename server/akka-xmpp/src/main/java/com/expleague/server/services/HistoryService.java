package com.expleague.server.services;

import akka.actor.UntypedActor;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.expleague.server.agents.GlobalChatAgent;
import com.expleague.server.agents.RoomAgent;
import com.expleague.server.agents.XMPP;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.expleague.UserHistoryQuery;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Stanza;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 13.02.17.
 */
public class HistoryService extends ActorAdapter<UntypedActor> {
  private static final Logger log = Logger.getLogger(HistoryService.class.getName());
  @ActorMethod
  public void invoke(Iq<UserHistoryQuery> iq) {
    final UserHistoryQuery answer = iq.get();

    final Timeout timeout = new Timeout(Duration.create(20, TimeUnit.SECONDS));
    final JID roomJid = XMPP.jid(GlobalChatAgent.ID);

    final Future<Object> ask = Patterns.ask(XMPP.register(roomJid, context()), new RoomAgent.DumpRequest(answer.client().local()), timeout);
    try {
      //noinspection unchecked
      final List<Stanza> result = (List<Stanza>) Await.result(ask, timeout.duration());
      result.forEach(answer::append);
      sender().tell(Iq.answer(iq, answer), self());
    }
    catch (Exception e) {
      log.log(Level.WARNING, "Unable to receive rooms for used " + answer.client() + "!", e);
      sender().tell(Iq.error(iq), self());
    }
  }
}