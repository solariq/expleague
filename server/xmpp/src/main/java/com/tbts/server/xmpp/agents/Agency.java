package com.tbts.server.xmpp.agents;

import akka.actor.ActorSelection;
import akka.actor.Props;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Presence;
import com.tbts.xmpp.stanza.Stanza;
import scala.concurrent.duration.Duration;

import java.util.HashMap;
import java.util.Map;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 21:59
 */
public class Agency extends UntypedActorAdapter {
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
    if (presence.to() != null) {
      allocate(presence.to()).tell(presence, self());
    }
  }

  private ActorSelection allocate(JID jid) {
    final String key = jid.bare().getAddr();
    ActorSelection selection = context().actorSelection("/user/xmpp/" + key);
    if (selection.resolveOne(Duration.Zero()).value().get().toOption().isEmpty()) {
      if (jid.domain().startsWith("muc."))
        selection = ActorSelection.apply(context().actorOf(Props.create(GroupChatAgent.class, jid.bare())), "/user/xmpp/" + key);
      else
        selection = ActorSelection.apply(context().actorOf(Props.create(UserAgent.class, jid.bare())), "/user/xmpp/" + key);
    }
    return selection;
  }
}
