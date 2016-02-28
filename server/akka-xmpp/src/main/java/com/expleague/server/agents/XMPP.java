package com.expleague.server.agents;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.util.Timeout;
import com.expleague.server.ExpLeagueServer;
import com.expleague.util.akka.AkkaTools;
import com.expleague.util.akka.UntypedActorAdapter;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;
import org.jetbrains.annotations.NotNull;
import scala.Option;
import scala.collection.JavaConversions;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 21:59
 */
public class XMPP extends UntypedActorAdapter {
  public static final String XMPP_ACTOR_PATH = "/user/xmpp";
  private static final JID myJid = JID.parse(ExpLeagueServer.config().domain());

  public static JID jid() {
    return myJid;
  }

  private final Subscriptions subscriptions = new Subscriptions();
  private final PresenceTracker presenceTracker = new PresenceTracker();

  public static ActorRef register(JID jid, ActorContext context) {
    // reply will call allocate via invoke(JID)
    return AkkaTools.askOrThrow(
      getXmppActorSelection(context),
      jid,
      Timeout.apply(Duration.create(60, TimeUnit.SECONDS))
    );
  }

  public static void send(Stanza message, ActorContext context) {
    getXmppActorSelection(context).forward(message, context);
  }

  public static void subscribe(JID forJid, ActorRef ref, ActorContext context) {
    getXmppActorSelection(context).tell(new Subscribe(jid(ref), forJid), ref);
  }

  public static ActorSelection agent(JID jid, ActorContext context) {
    return context.actorSelection(XMPP_ACTOR_PATH + "/" + jid.bare().toString());
  }

  public static JID jid(ActorRef ref) {
    return JID.parse(ref.path().name());
  }

  public void invoke(final JID jid) {
    sender().tell(findOrAllocate(jid), self());
  }

  public void invoke(Stanza stanza) {
    if (!stanza.isBroadcast() && !jid().bareEq(stanza.to())) {
      findOrAllocate(stanza.to()).forward(stanza, context());
    }
  }

  public void invoke(Presence presence) {
    if (!presence.isBroadcast()) {
      return;
    }

    if (!presenceTracker.updatePresence(presence)) {
      return;
    }

    final JID from = presence.from().bare();
    for (final ActorRef agent : JavaConversions.asJavaCollection(context().children())) {
      final JID actorJid = jid(agent);
      if (actorJid.local().isEmpty() || actorJid.bareEq(from)) {
        continue;
      }

      if (subscriptions.isSubscribed(actorJid, from)) {
        agent.tell(presence.<Presence>copy().to(actorJid), self());
      }
    }
  }

  public void invoke(Subscribe subscribe) {
    subscriptions.subscribe(subscribe);
    presenceTracker.notifyPresence(
      subscribe,
      presence -> sender().tell(presence, self())
    );
  }

  private ActorRef findOrAllocate(JID jid) {
    final String id = jid.bare().toString();
    final Option<ActorRef> child = context().child(id);
    if (child.isDefined()) {
      return child.get();
    }

    return context().actorOf(Props.create(getActorClass(jid), jid), id);
  }

  private static ActorSelection getXmppActorSelection(final ActorContext context) {
    return context.actorSelection(XMPP_ACTOR_PATH);
  }

  @NotNull
  private static Class<?> getActorClass(final JID jid) {
    return jid.domain().startsWith("muc.")
      ? TBTSRoomAgent.class
      : UserAgent.class;
  }

  public static class PresenceTracker {
    private final Map<JID, Presence> status = new HashMap<>();

    public boolean updatePresence(final Presence presence) {
      final JID from = presence.from().bare();
      return !presence.equals(status.put(from, presence));
    }

    public void notifyPresence(final Subscribe subscribe, final Consumer<Presence> callback) {
      final Presence presence = status.get(subscribe.forJid);
      if (presence != null) {
        callback.accept(presence.<Presence>copy().to(subscribe.from));
      }
    }
  }

  public static class Subscriptions {
    private final Map<JID, Set<JID>> subscription = new HashMap<>();

    public boolean isSubscribed(final JID subscriber, final JID target) {
      return subscription.getOrDefault(subscriber, Collections.emptySet()).contains(target);
    }

    public void subscribe(final Subscribe subscribe) {
      subscription.compute(subscribe.from, (jid, set) -> {
        if (set == null) {
          set = new HashSet<>();
        }
        set.add(subscribe.forJid);
        return set;
      });
    }
  }

  public static class Subscribe {
    private final JID from;
    private final JID forJid;

    public Subscribe(JID from, JID forJid) {
      this.from = from;
      this.forJid = forJid;
    }
  }
}
