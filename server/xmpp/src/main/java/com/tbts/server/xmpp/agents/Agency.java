package com.tbts.server.xmpp.agents;

import akka.actor.ActorRef;
import akka.actor.Props;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Presence;
import com.tbts.xmpp.stanza.Stanza;

import java.util.HashMap;
import java.util.Map;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 21:59
 */
public class Agency extends UntypedActorAdapter {
  private final Map<String, ActorRef> knownAgents = new HashMap<>();
  private final Map<JID, Presence> status = new HashMap<>();
  public void invoke(final JID jid) {
    getSender().tell(allocate(jid), getSelf());
  }

  public void invoke(Stanza msg) {
    if (msg.to() != null)
      allocate(msg.to()).forward(msg, getContext());
  }

  public void invoke(Presence presence) {
    status.put(presence.from(), presence);
  }

  private ActorRef allocate(JID jid) {
    final String key = jid.bare().getAddr();
    ActorRef actorRef = knownAgents.get(key);
    if (actorRef == null)
      knownAgents.put(key, actorRef = getContext().actorOf(Props.create(UserAgent.class, jid.bare()), jid.bare().toString()));
    return actorRef;
  }
}
