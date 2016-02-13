package com.expleague.server.agents;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.pattern.AskableActorSelection;
import akka.util.Timeout;
import com.expleague.server.ExpLeagueServer;
import com.expleague.util.akka.UntypedActorAdapter;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;
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

  public void invoke(Stanza stanza) {
    if (stanza.to() != null && !jid().bareEq(stanza.to()))
      allocate(stanza.to()).forward(stanza, getContext());
  }

  public void invoke(Presence presence) {
    if (presence.to() != null) // not broadcast
      return;
    final JID from = presence.from().bare();
    final Presence known = status.get(from);
    if (!presence.equals(known)) {
      status.put(from, presence);
      final Collection<ActorRef> children = JavaConversions.asJavaCollection(context().children());
      for (final ActorRef agent : children) {
        final JID actorJid = JID.parse(agent.path().name());
        if (actorJid.local().isEmpty() || actorJid.bareEq(from))
          continue;
        if (subscription.getOrDefault(actorJid, Collections.emptySet()).contains(from)) {
          final Presence copy = presence.copy();
          copy.to(actorJid);
          agent.tell(copy, self());
        }
      }
    }
  }

  private ActorRef allocate(JID jid) {
    String id = jid.bare().toString();
    Optional<ActorRef> existing = JavaConversions.asJavaCollection(context().children()).stream().filter(actorRef -> actorRef.path().name().equals(id)).findFirst();
    if (existing.isPresent())
      return existing.get();
    final Props props;
    if (jid.domain().startsWith("muc."))
      props = Props.create(TBTSRoomAgent.class, jid);
    else
      props = Props.create(UserAgent.class, jid);

    return context().actorOf(props, id);
  }

  public static void send(Stanza message, ActorContext context) {
    context.actorSelection(XMPP_ACTOR_PATH).forward(message, context);
  }

  public static ActorRef register(JID jid, ActorContext context) {
    final Future<Object> future = new AskableActorSelection(context.actorSelection(XMPP_ACTOR_PATH))
        .ask(jid, Timeout.apply(Duration.create(60, TimeUnit.SECONDS)));
    try {
      return (ActorRef) Await.result(future, Duration.Inf());
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  private static JID myJid = JID.parse(ExpLeagueServer.config().domain());
  public static JID jid() {
    return myJid;
  }

  public Map<JID, Set<JID>> subscription = new HashMap<>();
  public void invoke(Subscribe subscribe) {
    subscription.compute(subscribe.from, (jid, set) -> {
      if (set == null)
        set = new HashSet<>();
      set.add(subscribe.forJid);
      return set;
    });
    status.computeIfPresent(subscribe.forJid, (jid, presence) -> {
      final Presence copy = presence.copy();
      copy.to(subscribe.from);
      sender().tell(copy, self());
      return presence;
    });
  }

  public static void subscribe(JID forJid, ActorRef ref, ActorContext context) {
    context.actorSelection(XMPP_ACTOR_PATH).tell(new Subscribe(JID.parse(ref.path().name()), forJid), ref);
  }

  public static ActorSelection agent(JID room, ActorContext context) {
    return context.actorSelection(XMPP_ACTOR_PATH + "/" + room.bare().toString());
  }

  public static JID jid(ActorRef ref) {
    return JID.parse(ref.path().name());
  }

  public static class Subscribe {
    private final JID forJid;
    private final JID from;

    public Subscribe(JID from, JID forJid) {
      this.forJid = forJid;
      this.from = from;
    }
  }
}