package com.expleague.server.services;

import akka.actor.UntypedActor;
import com.expleague.model.Pattern;
import com.expleague.server.dao.PatternsRepository;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.control.expleague.PatternsQuery;
import com.expleague.xmpp.stanza.Iq;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 28/03/16.
 */
public class PatternsService extends ActorAdapter<UntypedActor> {
  @ActorMethod
  public void invoke(Iq<PatternsQuery> rosterIq) {
    try (final Stream<Pattern> all = PatternsRepository.instance().all()) {
      switch (rosterIq.get().intent()) {
        case PRESENTATION:
          sender().tell(Iq.answer(rosterIq, new PatternsQuery(all.map(
              Pattern::presentation
          ).filter(Objects::nonNull).collect(Collectors.toList()))), self());
          break;
        case WORK:
          sender().tell(Iq.answer(rosterIq, new PatternsQuery(all.collect(Collectors.toList()))), self());
          break;
      }
    }
  }
}
