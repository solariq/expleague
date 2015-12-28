package com.tbts.server.agents;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.pattern.AskableActorSelection;
import akka.util.Timeout;
import com.tbts.server.TBTSServer;
import com.tbts.util.akka.AkkaTools;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Presence;
import com.tbts.xmpp.stanza.Stanza;
import scala.collection.JavaConversions;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.*;
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
    final JID from = presence.from().bare();
    final Presence known = status.get(from);
    if (!presence.equals(known)) {
      status.put(from, presence);
      for (final ActorRef agent : JavaConversions.asJavaCollection(context().children())) {
        final JID jid = JID.parse(agent.path().name());
        if (XMPP.jid().bareEq(from) || subscription.getOrDefault(jid, Collections.emptySet()).contains(from)) {
          final Presence copy = presence.copy();
          copy.to(jid);
          agent.tell(copy, self());
        }
        else if (jid.equals(presence.to())) {
          agent.tell(presence, self());
        }
      }
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

  public Map<JID, Set<JID>> subscription = new HashMap<>();
  public void invoke(Subscribe subscribe) {
    if (!XMPP.jid().bareEq(subscribe.forJid)) {
      subscription.compute(subscribe.from, (jid, set) -> {
        if (set == null)
          set = new HashSet<>();
        set.add(subscribe.forJid);
        return set;
      });
    }
    status.computeIfPresent(subscribe.forJid, (jid, presence) -> {
      sender().tell(presence, self());
      return presence;
    });
  }
  public static class Subscribe {
    private final JID forJid;
    private final JID from;

    public Subscribe(JID forJid, JID from) {
      this.forJid = forJid;
      this.from = from;
    }
  }
}
