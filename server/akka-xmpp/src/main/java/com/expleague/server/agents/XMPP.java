package com.expleague.server.agents;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.expleague.model.Application;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.Roster;
import com.expleague.server.Subscription;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.util.akka.AkkaTools;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;
import com.google.common.annotations.VisibleForTesting;
import com.spbsu.commons.util.Pair;
import org.jetbrains.annotations.NotNull;
import scala.Option;
import scala.collection.JavaConversions;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 21:59
 */
public class XMPP extends ActorAdapter<UntypedActor> {
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
        context.actorSelection(XMPP_ACTOR_PATH),
        jid,
        Timeout.apply(Duration.create(60, TimeUnit.SECONDS))
    );
  }

  public static void send(Stanza message, ActorContext context) {
    context.actorSelection(XMPP_ACTOR_PATH).forward(message, context);
  }

  public static void subscribe(Subscription subscription, ActorContext context) {
    context.actorSelection(XMPP_ACTOR_PATH).forward(new SubscriptionRequest(subscription, true), context);
  }

  public static void unsubscribe(Subscription subscription, ActorContext context) {
    context.actorSelection(XMPP_ACTOR_PATH).forward(new SubscriptionRequest(subscription, false), context);
  }

  public static Set<JID> online(ActorContext context) {
    try {
      final Timeout timeout = Timeout.apply(30, TimeUnit.SECONDS);
      final Future<Object> future = Patterns.ask(context.actorSelection(XMPP_ACTOR_PATH), Presence.class, timeout);
      //noinspection unchecked
      return (Set<JID>)Await.result(future, timeout.duration());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

//  public static boolean online(JID jid, ActorContext context) {
//    try {
//      final Timeout timeout = Timeout.apply(5, TimeUnit.SECONDS);
//      final Future<Object> future = Patterns.ask(context.actorSelection(XMPP_ACTOR_PATH), Pair.create(jid, XMPPDevice.class), timeout);
//      final XMPPDevice[] result = (XMPPDevice[])Await.result(future, timeout.duration());
//      return result.length > 0;
//    } catch (Exception e) {
//      throw new RuntimeException(e);
//    }
//  }

  public static <T> void whisper(JID to, T what, ActorContext context) {
    context.actorSelection(XMPP_ACTOR_PATH).forward(Pair.create(to, what), context);
  }

  public static JID jid(ActorRef ref) {
    return JID.parse(ref.path().name().replace('&', '/'));
  }

  @Override
  protected void preStart() throws Exception {
    findOrAllocate(jid(GlobalChatAgent.ID));
  }

  @ActorMethod
  public void invoke(Pair<JID, ?> whisper) {
    findOrAllocate(whisper.first).forward(whisper.second, context());
  }

  @SuppressWarnings("UnusedParameters")
  @ActorMethod
  public void invoke(Class<Presence> clazz) {
    sender().tell(presenceTracker.online(), self());
  }

  @ActorMethod
  public void invoke(final JID jid) {
    sender().tell(findOrAllocate(jid), self());
  }

  @ActorMethod
  public void invoke(Stanza stanza) {
    if (!stanza.isBroadcast() && !jid().bareEq(stanza.to())) {
      findOrAllocate(stanza.to()).forward(stanza, context());
    }
  }

  @ActorMethod
  public void invoke(Message message) {
    if (jid().bareEq(message.to()) && message.has(Application.class)) {
      Roster.instance().application(message.get(Application.class), message.from());
    }
  }

  @ActorMethod
  public void invoke(Presence presence) {
    if (!presence.isBroadcast() || !presenceTracker.updatePresence(presence))
      return;

    final JID from = presence.from();
    for (final ActorRef agent : JavaConversions.asJavaCollection(context().children())) {
      final JID actorJid = jid(agent);
      subscriptions.subscribed(actorJid, from).forEach(jid ->
          agent.tell(presence.<Presence>copy().to(jid), self())
      );
    }
  }

  @ActorMethod
  public void invoke(SubscriptionRequest request) {
    if (request.appendOrRemove) {
      subscriptions.subscribe(request.subscription);
      presenceTracker.notifyPresence(request.subscription, presence -> XMPP.send(presence, context()));
    }
    else subscriptions.unsubscribe(request.subscription);
  }

  private ActorRef findOrAllocate(JID jid) {
    final String id = jid.bare().toString();
    final Option<ActorRef> child = context().child(id);
    if (child.isDefined()) {
      return child.get();
    }

    return context().actorOf(newActorProps(jid), id);
  }

  @VisibleForTesting
  @NotNull
  protected Props newActorProps(final JID jid) {
    if (GlobalChatAgent.ID.equals(jid.local()))
      return props(GlobalChatAgent.class, jid.bare());
    else if (jid.domain().startsWith("muc."))
      return props(ExpLeagueRoomAgent.class, jid.bare());
    else
      return props(UserAgent.class, jid.bare());
  }

  public static JID jid(String local) {
    return new JID(local, ExpLeagueServer.config().domain(), null);
  }

  public static JID muc(String local) {
    return new JID(local, "muc." + ExpLeagueServer.config().domain(), null);
  }

  public static class PresenceTracker {
    private final Map<JID, Presence> status = new HashMap<>();

    public boolean updatePresence(final Presence presence) {
      final JID from = presence.from().bare();
      return !presence.equals(status.put(from, presence));
    }

    public void notifyPresence(final Subscription subscription, Consumer<Presence> callback) {
      status.forEach((jid, presence) -> {
        if (subscription.relevant(jid)) {
          callback.accept(presence.<Presence>copy().to(subscription.who()));
        }
      });
    }

    public Set<JID> online() {
      return status.entrySet().stream().filter(
          entry -> entry.getValue().available()
      ).map(Map.Entry::getKey).map(JID::bare).collect(Collectors.toSet());
    }

    public Presence status(JID jid) {
      return status.getOrDefault(jid, new Presence(jid, false));
    }
  }

  public static class Subscriptions {
    private final Map<JID, Set<Subscription>> subscriptions = new HashMap<>();

    public Stream<JID> subscribed(final JID subscriber, final JID target) {
      return subscriptions.getOrDefault(subscriber, Collections.emptySet()).stream()
          .filter(s -> s.relevant(target)).map(Subscription::who);
    }

    public void subscribe(final Subscription subscription) {
      subscriptions.compute(subscription.who().bare(), (jid, set) -> {
        if (set == null) {
          set = new HashSet<>();
        }
        set.add(subscription);
        return set;
      });
    }

    public void unsubscribe(Subscription subscription) {
      final Set<Subscription> set = this.subscriptions.get(subscription.who().bare());
      if (set != null)
        set.remove(subscription);
    }
  }

  private static class SubscriptionRequest {
    Subscription subscription;
    boolean appendOrRemove;

    public SubscriptionRequest(Subscription subscription, boolean appendOrRemove) {
      this.subscription = subscription;
      this.appendOrRemove = appendOrRemove;
    }
  }
}
