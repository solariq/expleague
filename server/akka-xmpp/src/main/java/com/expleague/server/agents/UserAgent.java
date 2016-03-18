package com.expleague.server.agents;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.PoisonPill;
import akka.pattern.Patterns;
import akka.persistence.RecoveryCompleted;
import com.expleague.model.Delivered;
import com.expleague.model.Operations;
import com.expleague.server.Roster;
import com.expleague.server.XMPPDevice;
import com.expleague.server.XMPPUser;
import com.expleague.util.akka.ActorMethod;
import com.expleague.util.akka.PersistentActorAdapter;
import com.expleague.util.akka.PersistentActorContainer;
import com.expleague.util.ios.NotificationsManager;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;
import com.relayrides.pushy.apns.util.TokenUtil;
import scala.Option;
import scala.collection.JavaConversions;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 20:40
 */
public class UserAgent extends PersistentActorAdapter {
  private static final Logger log = Logger.getLogger(UserAgent.class.getName());
  private final JID bareJid;
  private final Map<JID, Presence> presenceMap = new HashMap<>();
  private final List<XMPPDevice> connected = new ArrayList<>();
  private final XMPPUser user;

  public UserAgent(JID jid) {
    this.bareJid = jid.bare();
    user = Roster.instance().user(jid.local());
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
    final XMPPDevice device;

    public ConnStatus(boolean connected, String resource, XMPPDevice device) {
      this.connected = connected;
      this.resource = resource;
      this.device = device;
    }
  }

  @ActorMethod
  public void invoke(ConnStatus status) { // connection acquired
    final String resource = status.resource;
    final String actorResourceAddr = resource.isEmpty() ? "@@empty@@" : resource.replace('/', '&');
    final Option<ActorRef> option = context().child(actorResourceAddr);
    if (status.connected) {
      final ActorRef courier;
      if (option.isDefined()) {
        log.warning("Concurrent connectors for the same resource: " + resource + " for " + jid() + "!");
        courier = option.get();
        courier.tell(PoisonPill.getInstance(), self());
        try {
          Thread.sleep(1000);
        }
        catch (InterruptedException ignore) {}
        self().forward(status, context());
        return;
      }
      connected.add(status.device);
      final ActorRef courierRef = context().actorOf(
          PersistentActorContainer.props(Courier.class, status.device, sender()),
          actorResourceAddr
      );
      sender().tell(courierRef, self());

      presenceMap.values().stream()
          .filter(Presence::available)
          .forEach(p -> sender().tell(p, self()));
    }
    else {
      if (option.isDefined())
        context().stop(option.get());
      connected.remove(status.device);
      if (connected.isEmpty())
        invoke(new Presence(new JID(jid().local(), jid().domain(), resource), false));
    }
  }

  @ActorMethod
  public void devices(Class<XMPPDevice> clazz) {
    sender().tell(connected.toArray(new XMPPDevice[connected.size()]), self());
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
    if (!(stanza instanceof Iq) && stanza.from().resource().endsWith("expert"))
      LaborExchange.Experts.tellTo(jid(), stanza, self(), context());
  }

  private void toConn(Stanza stanza) {
    final Collection<ActorRef> couriers = JavaConversions.asJavaCollection(context().children().seq());
    for (final ActorRef courier : couriers) {
      courier.tell(stanza, self());
    }
    if (couriers.isEmpty() && stanza instanceof Message) {
      Stream.of(user.devices()).map(XMPPDevice::token).filter(s -> s != null).forEach(
          token -> NotificationsManager.instance().sendPush((Message)stanza, token)
      );
    }
  }

  @Override
  public String persistenceId() {
    return bareJid.getAddr();
  }

  public static class Courier extends PersistentActorAdapter {
    private final XMPPDevice connectedDevice;
    private final ActorRef connection;
    private Queue<Stanza> deliveryQueue = new ArrayDeque<>();

    public Courier(XMPPDevice connectedDevice, ActorRef connection) {
      this.connectedDevice = connectedDevice;
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
      if (peek != null && !peek.id().equals(delivered.id())) {
        log.warning("Unexpected delivery message id " + delivered.id() + ", retrying to send: " + peek);
//        connection.tell(peek, self());
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
      return connectedDevice != null ? connectedDevice.user().jid().toString() : "fake";
    }
  }
}
