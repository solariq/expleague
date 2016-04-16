package com.expleague.server.services;

import akka.actor.UntypedActor;
import com.expleague.model.Pattern;
import com.expleague.model.Tag;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.dao.PatternsRepository;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.util.akka.UntypedActorAdapter;
import com.expleague.xmpp.control.expleague.Intent;
import com.expleague.xmpp.control.expleague.PatternsQuery;
import com.expleague.xmpp.control.expleague.TagsQuery;
import com.expleague.xmpp.stanza.Iq;

import java.util.stream.Collectors;

/**
 * Experts League
 * Created by solar on 28/03/16.
 */
public class PatternsService extends ActorAdapter<UntypedActor> {
  @ActorMethod
  public void invoke(Iq<PatternsQuery> rosterIq) {
    switch (rosterIq.get().intent()) {
      case PRESENTATION:
        sender().tell(Iq.answer(rosterIq, new PatternsQuery(PatternsRepository.instance().all().map(
            Pattern::presentation
        ).collect(Collectors.toList()))), self());
        break;
      case WORK:
        sender().tell(Iq.answer(rosterIq, new PatternsQuery(PatternsRepository.instance().all().collect(Collectors.toList()))), self());
        break;
    }
  }
}
