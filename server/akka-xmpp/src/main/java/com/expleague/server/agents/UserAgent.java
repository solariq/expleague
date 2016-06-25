package com.expleague.server.agents;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.persistence.RecoveryCompleted;
import com.expleague.model.Answer;
import com.expleague.model.Delivered;
import com.expleague.model.Operations;
import com.expleague.server.Subscription;
import com.expleague.server.XMPPDevice;
import com.expleague.server.notifications.NotificationsManager;
import com.expleague.util.akka.ActorMethod;
import com.expleague.util.akka.PersistentActorAdapter;
import com.expleague.util.akka.PersistentActorContainer;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;
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
  private final List<XMPPDevice> connected = new ArrayList<>();
  private Map<String, Subscription> subscriptions = new HashMap<>();

  public UserAgent(JID jid) {
    this.bareJid = jid.bare();
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
    final JID deviceJid = new JID(jid().local(), jid().domain(), resource);
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
      if (!status.device.expert()) {
        final ClientSubscription subscription = new ClientSubscription(bareJid);
        subscriptions.put(resource, subscription);
        XMPP.subscribe(subscription, context());
      }
      sender().tell(courierRef, self());
    }
    else {
      if (option.isDefined())
        context().stop(option.get());
      connected.remove(status.device);
      invoke(new Presence(deviceJid, false));
      final Subscription subscription = subscriptions.remove(resource);
      if (subscription != null) {
        XMPP.unsubscribe(subscription, context());
      }
    }
  }

  @ActorMethod
  public void devices(Class<XMPPDevice> clazz) {
    sender().tell(connected.toArray(new XMPPDevice[connected.size()]), self());
  }

  @ActorMethod
  public void invoke(final Stanza sta) {
    if (!sta.from().bareEq(jid())) { // incoming
      if (sta instanceof Message) {
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
    if (couriers.isEmpty() && stanza instanceof Message)
      NotificationsManager.send((Message)stanza, context());
  }

  @Override
  public String persistenceId() {
    return bareJid.getAddr();
  }

  public static class Courier extends PersistentActorAdapter {
    private final XMPPDevice connectedDevice;
    private final ActorRef connection;
    private Deque<Stanza> deliveryQueue = new ArrayDeque<>();
    private Set<String> confirmationAwaitingStanzas = new HashSet<>();

    public Courier(XMPPDevice connectedDevice, ActorRef connection) {
      this.connectedDevice = connectedDevice;
      this.connection = connection;
    }

    @Override
    public void onReceiveRecover(Object o) throws Exception {
      if (o instanceof Stanza) {
        final Stanza stanza = (Stanza) o;
        deliveryQueue.add(stanza);
      }
      else if (o instanceof Delivered) {
        final Iterator<Stanza> riter = deliveryQueue.descendingIterator();
        while (riter.hasNext()) {
          if (riter.next().id().equals(((Delivered)o).id())) {
            riter.remove();
            break;
          }
        }
      }
      else if (o instanceof RecoveryCompleted) {
        nextChunk();
      }
    }

    @ActorMethod
    public void invoke(final Delivered delivered) {
      final String id = delivered.id();
      if (confirmationAwaitingStanzas.remove(id)) {
        nextChunk();
        context().parent().forward(delivered, context());
        NotificationsManager.delivered(id, connectedDevice, context());
      }
      else log.warning("Unexpected delivery message id " + delivered.id());
    }

    @ActorMethod
    public void invoke(final Stanza stanza) {
      if (!(stanza instanceof Presence)) { // delivery confirmation required
        deliveryQueue.add(stanza);
        nextChunk();
      }
      else connection.tell(stanza, self());
    }

    boolean border = false;
    private void nextChunk() {
      if (border && !confirmationAwaitingStanzas.isEmpty())
        return;

      border = false;
      Stanza poll;
      do {
        poll = deliveryQueue.poll();
        if (poll == null)
          break;
        confirmationAwaitingStanzas.add(poll.id());
        connection.tell(poll, self());
      }
      while(!(border = isDeliveryOrderRequired(poll)));
    }

    protected boolean isDeliveryOrderRequired(final Stanza stanza) {
      if (stanza instanceof Message) {
        final Message message = (Message) stanza;
        return message.has(Operations.Command.class) || message.has(Answer.class);
      }
      return false;
    }

    @Override
    public String persistenceId() {
      return connectedDevice != null ? connectedDevice.user().jid().toString() : "fake";
    }
  }

}
