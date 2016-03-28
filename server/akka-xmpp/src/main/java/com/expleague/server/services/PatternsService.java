package com.expleague.server.services;

import com.expleague.model.Tag;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.dao.PatternsRepository;
import com.expleague.util.akka.UntypedActorAdapter;
import com.expleague.xmpp.control.expleague.PatternsQuery;
import com.expleague.xmpp.control.expleague.TagsQuery;
import com.expleague.xmpp.stanza.Iq;

import java.util.stream.Collectors;

/**
 * Experts League
 * Created by solar on 28/03/16.
 */
public class PatternsService extends UntypedActorAdapter {
  public void invoke(Iq<PatternsQuery> rosterIq) {
    sender().tell(Iq.answer(rosterIq, new PatternsQuery(PatternsRepository.instance().all().collect(Collectors.toList()))), self());
  }
}
