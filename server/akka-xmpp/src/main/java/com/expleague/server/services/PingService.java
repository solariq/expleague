package com.expleague.server.services;

import akka.actor.UntypedActor;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.control.expleague.PatternsQuery;
import com.expleague.xmpp.stanza.Iq;

/**
 * Experts League
 * Created by solar on 26/04/16.
 */
public class PingService extends ActorAdapter<UntypedActor> {
  @ActorMethod
  public void invoke(Iq<PatternsQuery> rosterIq) {
    sender().tell(Iq.answer(rosterIq), self());
  }
}
