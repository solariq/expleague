package com.tbts.server.agents;

import akka.actor.*;
import akka.pattern.AskableActorSelection;
import akka.util.Timeout;
import com.tbts.server.TBTSServer;
import com.tbts.util.akka.AkkaTools;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Presence;
import com.tbts.xmpp.stanza.Stanza;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 21:59
 */
public class XMPP extends UntypedActorAdapter {
  public static final String XMPP_ACTOR_PATH = "/user/xmpp";
  private final Map<JID, Presence> status = new HashMap<>();

  public void invoke(final JID jid) {
    sender().tell(allocate(jid), getSelf());
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

  private ActorRef allocate(JID jid) {
    return AkkaTools.getOrCreate(jid.bare().toString(), context(), () -> {
      if (jid.domain().startsWith("muc."))
        return Props.create(TBTSRoomAgent.class, jid);
      else
        return Props.create(UserAgent.class, jid);
    });
  }

  public static void send(Stanza message, ActorContext context) {
    context.actorSelection(XMPP_ACTOR_PATH).forward(message, context);
  }

  public static ActorRef register(JID jid, ActorContext context) {
    final Future<Object> future = new AskableActorSelection(context.actorSelection(XMPP_ACTOR_PATH))
        .ask(jid, Timeout.apply(Duration.create(1, TimeUnit.SECONDS)));
    try {
      return (ActorRef) Await.result(future, Duration.Inf());
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  private static JID myJid = JID.parse(TBTSServer.config().domain());
  public static JID jid() {
    return myJid;
  }
}
