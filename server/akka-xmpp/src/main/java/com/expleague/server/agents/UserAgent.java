package com.expleague.server.agents;

import akka.actor.ActorRef;
import akka.persistence.RecoveryCompleted;
import com.expleague.model.Delivered;
import com.expleague.model.Operations;
import com.expleague.util.akka.ActorMethod;
import com.expleague.util.akka.PersistentActorAdapter;
import com.expleague.util.akka.PersistentActorContainer;
import com.expleague.util.ios.NotificationsManager;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;
import com.relayrides.pushy.apns.util.TokenUtil;
import scala.Option;
import scala.collection.JavaConversions;

import java.util.*;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 20:40
 */
public class UserAgent extends PersistentActorAdapter {
  private static final Logger log = Logger.getLogger(UserAgent.class.getName());
  private final JID bareJid;
  private final Map<JID, Presence> presenceMap = new HashMap<>();
  private Set<String> appTokens = new HashSet<>();

  public UserAgent(JID jid) {
    this.bareJid = jid.bare();
  }

  @Override
  protected void init() {
    XMPP.subscribe(XMPP.jid(), self(), context());
  }

  public JID jid() {
    return bareJid;
  }

  public static class ConnStatus {
    final boolean connected;
    final String resource;

    public ConnStatus(boolean connected, String resource) {
      this.connected = connected;
      this.resource = resource;
    }
  }

  @ActorMethod
  public void invoke(ConnStatus status) { // connection acquired
    final String resource = status.resource;
    final Option<ActorRef> option = context().child(resource);
    if (status.connected) {
      final ActorRef courier;
      if (!option.isEmpty()) {
        log.warning("Concurrent connectors for the same resource: " + resource + " for " + jid() + "!");
        courier = option.get();
        context().stop(courier);
        try {
          Thread.sleep(10);
        }
        catch (InterruptedException ignore) {}
        invoke(status);
      }
      else {
        final ActorRef courierRef = context().actorOf(
            PersistentActorContainer.props(Courier.class, jid().resource(resource), sender()),
            resource.isEmpty() ? "@@empty@@" : resource
        );
        sender().tell(courierRef, self());
      }

      presenceMap.values().stream()
          .filter(Presence::available)
          .forEach(p -> sender().tell(p, self()));
    }
    else {
      if (option.isDefined())
        context().stop(option.get());
      invoke(new Presence(new JID(jid().local(), jid().domain(), resource), false));
    }
  }

  @ActorMethod
  public void invoke(final Stanza sta) {
    if (!sta.from().bareEq(jid())) { // incoming
      if (sta instanceof Presence) {
        final Presence replace = presenceMap.put(sta.from(), (Presence)sta);
        if (!sta.equals(replace))
          toConn(sta);
      }
      else if (sta instanceof Message) {
        persist(sta, this::toConn);
      }
      else toConn(sta);
    }
    else toWorld(sta);
  }

  @ActorMethod
  public void invoke(final Delivered delivered) {
    persist(delivered, param -> {});
  }

  private void toWorld(Stanza stanza) {
    XMPP.send(stanza, context());
    if("expert".equals(stanza.from().resource()))
      LaborExchange.Experts.tellTo(jid(), stanza, self(), context());
    if (stanza instanceof Message) {
      final Message message = (Message) stanza;
      if (message.has(Operations.Token.class)) {
        appTokens.add(TokenUtil.sanitizeTokenString(message.get(Operations.Token.class).value()));
      }
    }
  }

  private void toConn(Stanza stanza) {
    final Collection<ActorRef> couriers = JavaConversions.asJavaCollection(context().children().seq());
    for (final ActorRef courier : couriers) {
      courier.tell(stanza, self());
    }
    if (couriers.isEmpty() && stanza instanceof Message) {
      appTokens.stream().forEach(
          token -> NotificationsManager.instance().sendPush((Message)stanza, token)
      );
    }
  }

  @Override
  public String persistenceId() {
    return bareJid.getAddr();
  }

  public static class Courier extends PersistentActorAdapter {
    private final JID jid;
    private final ActorRef connection;
    private Queue<Stanza> deliveryQueue = new ArrayDeque<>();

    public Courier(JID jid, ActorRef connection) {
      this.jid = jid;
      this.connection = connection;
    }

    @Override
    public void onReceiveRecover(Object o) throws Exception {
      if (o instanceof Stanza) {
        deliveryQueue.add((Stanza) o);
      }
      else if (o instanceof Delivered) {
        deliveryQueue.stream().filter(
            stanza -> stanza.id().equals(((Delivered)o).id())
        ).findFirst().ifPresent(deliveryQueue::remove);
      }
      else if (o instanceof RecoveryCompleted && !deliveryQueue.isEmpty()) {
        connection.tell(deliveryQueue.peek(), self());
      }
    }

    @ActorMethod
    public void invoke(final Delivered delivered) {
      final Stanza peek = deliveryQueue.peek();
      if (!peek.id().equals(delivered.id())) {
        log.warning("Unexpected delivery message id " + delivered.id() + ", retrying to send: " + peek);
        connection.tell(peek, self());
        return;
      }
      deliveryQueue.poll();
      if (!deliveryQueue.isEmpty()) {
        connection.tell(deliveryQueue.peek(), self());
      }
      context().parent().forward(delivered, context());
    }

    @ActorMethod
    public void invoke(final Stanza stanza) {
      if (stanza instanceof Presence) {
        connection.tell(stanza, self());
        return;
      }
      if (deliveryQueue.isEmpty())
        connection.tell(stanza, self());
      deliveryQueue.add(stanza);
    }

    @Override
    public String persistenceId() {
      return jid.bare().toString();
    }
  }
}
