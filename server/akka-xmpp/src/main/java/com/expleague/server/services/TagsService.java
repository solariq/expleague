package com.expleague.server.services;

import com.expleague.server.agents.LaborExchange;
import com.expleague.util.akka.UntypedActorAdapter;
import com.expleague.xmpp.control.expleague.TagsQuery;
import com.expleague.xmpp.stanza.Iq;

import java.util.stream.Collectors;

/**
 * Experts League
 * Created by solar on 28/03/16.
 */
public class TagsService extends UntypedActorAdapter {
  public void invoke(Iq<TagsQuery> rosterIq) {
    sender().tell(Iq.answer(rosterIq, new TagsQuery(LaborExchange.board().tags().collect(Collectors.toList()))), self());
  }
}
