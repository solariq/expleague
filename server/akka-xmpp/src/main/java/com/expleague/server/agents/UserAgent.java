package com.expleague.server.agents;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.persistence.DeleteMessagesFailure;
import akka.persistence.DeleteMessagesSuccess;
import akka.persistence.RecoveryCompleted;
import com.expleague.model.Answer;
import com.expleague.model.Delivered;
import com.expleague.model.Operations;
import com.expleague.server.Subscription;
import com.expleague.server.XMPPDevice;
import com.expleague.server.agents.subscription.ClientSubscription;
import com.expleague.server.agents.subscription.DefaultSubscription;
import com.expleague.server.notifications.NotificationsManager;
import com.expleague.util.akka.ActorMethod;
import com.expleague.util.akka.PersistentActorAdapter;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.muc.MucHistory;
import com.expleague.xmpp.muc.MucXData;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;
import org.jetbrains.annotations.NotNull;
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
  private final Map<String, XMPPDevice> connected = new HashMap<>();
//  private Map<String, Subscription> subscriptions = new HashMap<>();

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
    final String actorResourceAddr = courierActorName(deviceJid);
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
      connected.put(resource, status.device);
      final ActorRef courierRef = context().actorOf(
          props(Courier.class, status.device, deviceJid, sender()),
          actorResourceAddr
      );
      sender().tell(courierRef, self());
    }
    else {
      if (option.isDefined())
        context().stop(option.get());
      if (connected.size() == 1)
        invoke(new Presence(deviceJid, false));
      connected.remove(resource);
    }
  }

  @NotNull
  private String courierActorName(JID jid) {
    final String resource = jid.resource();
    return resource.isEmpty() ? "@@empty@@" : resource.replace('/', '&');
  }

  @ActorMethod
  public void devices(Class<XMPPDevice> clazz) {
    sender().tell(connected.values().toArray(new XMPPDevice[connected.size()]), self());
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
    if (delivered.resource() != null) // delivered to the concrete device
      persist(delivered, param -> {});
  }

  private void toWorld(Stanza stanza) {
    XMPP.send(stanza, context());
    final XMPPDevice device = device(stanza.from());
    if (!(stanza instanceof Iq) && (device != null && device.expert()))
      LaborExchange.Experts.tellTo(jid(), stanza, self(), context());
  }

  private void toConn(Stanza stanza) {
    final String resource = stanza.to().resource();
    if (!resource.isEmpty()) {
      final Option<ActorRef> child = context().child(courierActorName(stanza.to()));
      if (child.isDefined())
        child.get().tell(stanza, self());
      else if (stanza instanceof Message)
        NotificationsManager.send((Message)stanza, context());
      else
        log.fine("Stanza " + stanza.xmlString() + " was not delivered: no courier found");
    }
    else {
      final Collection<ActorRef> couriers = JavaConversions.asJavaCollection(context().children().seq());
      if (couriers.isEmpty())
        log.fine("Stanza " + stanza.xmlString() + " was not delivered: no courier found");
      for (final ActorRef courier : couriers) {
        courier.tell(stanza, self());
      }
      if (couriers.isEmpty() && stanza instanceof Message)
        NotificationsManager.send((Message)stanza, context());
    }
  }

  private XMPPDevice device(JID from) {
    XMPPDevice device = connected.get(from.resource());
    if (device == null)
      device = XMPPDevice.fromJid(from);
    return device;
  }

  @Override
  public String persistenceId() {
    return bareJid.getAddr();
  }

  public static class Courier extends PersistentActorAdapter {
    private final XMPPDevice connectedDevice;
    private final ActorRef connection;
    private final JID deviceJid;
    private Deque<Stanza> deliveryQueue = new ArrayDeque<>();
    private Set<String> confirmationAwaitingStanzas = new HashSet<>();
    private Map<String, Stanza> inFlight = new HashMap<>();
    private final Subscription subscription;
    private int totalMessages;

    public Courier(XMPPDevice connectedDevice, JID deviceJid, ActorRef connection) {
      this.connectedDevice = connectedDevice;
      this.deviceJid = deviceJid;
      this.connection = connection;
      if (EnumSet.of(XMPPDevice.Role.ADMIN, XMPPDevice.Role.EXPERT).contains(connectedDevice.role()))
        subscription = new DefaultSubscription(deviceJid);
      else
        subscription = new ClientSubscription(deviceJid);
    }

    @Override
    protected void preStart() throws Exception {
      totalMessages = 0;
    }

    @ActorMethod
    public void repackDeliveryQueue(DeleteMessagesSuccess success) {
      deliveryQueue.forEach(stanza -> persist(stanza, s -> {}));
      totalMessages = deliveryQueue.size();
    }

    @ActorMethod
    public void failedToDeleteMessaged(DeleteMessagesFailure failure) {
      log.warning("Unable to clear out user mailbox: " + deviceJid);
    }

    @Override
    public void onReceiveRecover(Object o) throws Exception {
      if (o instanceof Stanza) {
        final Stanza stanza = (Stanza) o;
        totalMessages++;
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
        if (totalMessages > 100 && deliveryQueue.size() < 50) {
          // TODO: snapshot
          deleteMessages(); // clear the mailbox
        }

        nextChunk();
        XMPP.subscribe(subscription, context());
        if (connectedDevice.role() == XMPPDevice.Role.ADMIN) {
          final Presence presence = new Presence(deviceJid, XMPP.jid(GlobalChatAgent.ID), true);
          final MucHistory history = new MucHistory();
          history.recent(true);
          presence.append(new MucXData(history));
          XMPP.send(presence, context());
        }
        invoke(new Presence(deviceJid, true));
      }
    }

    @Override
    protected void postStop() {
      XMPP.unsubscribe(subscription, context());
    }

    @ActorMethod
    public void invoke(final Delivered delivered) {
      final String id = delivered.id();
      if (confirmationAwaitingStanzas.remove(id)) {
        final Stanza remove = inFlight.remove(id);
        XMPP.whisper(remove.from(), new Delivered(id), context());
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
        inFlight.put(poll.id(), poll);
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
