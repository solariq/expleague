package com.tbts.server.xmpp.agents;

import akka.actor.ActorNotFound;
import akka.actor.ActorSelection;
import akka.actor.Props;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Presence;
import com.tbts.xmpp.stanza.Stanza;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    final ActorSelection selection = context().actorSelection("/user/xmpp/" + key);
    try {
      Await.result(selection.resolveOne(Duration.create(1, TimeUnit.SECONDS)), Duration.Inf());
    }
    catch (ActorNotFound anf){
      if (jid.domain().startsWith("muc."))
        context().actorOf(Props.create(GroupChatAgent.class, jid), key);
      else
        context().actorOf(Props.create(UserAgent.class, jid), key);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    return selection;
  }
}
