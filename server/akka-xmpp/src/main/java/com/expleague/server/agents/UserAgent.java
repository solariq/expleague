package com.expleague.server.agents;

import akka.actor.ActorRef;
import akka.persistence.SaveSnapshotFailure;
import akka.persistence.SaveSnapshotSuccess;
import com.expleague.model.Operations;
import com.expleague.util.akka.ActorMethod;
import com.expleague.util.akka.PersistentActorAdapter;
import com.expleague.util.ios.NotificationsManager;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;
import com.relayrides.pushy.apns.util.TokenUtil;
import com.spbsu.commons.util.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 20:40
 */
public class UserAgent extends PersistentActorAdapter {
  private static final Logger log = Logger.getLogger(UserAgent.class.getName());

  private final JID bareJid;

  private final MessagesStash messagesStash;
  private final ConnectionManager connectionManager;
  private final PresenceTracker presenceTracker;
  private final NotificationController notificationController;

  public UserAgent(JID jid) {
    this.bareJid = jid.bare();
    this.messagesStash = new MessagesStash(this);
    this.connectionManager = new ConnectionManager(this);
    this.presenceTracker = new PresenceTracker();
    this.notificationController = new NotificationController();

    log.fine("User agent in created for " + bareJid);
  }

  @Override
  protected void init() {
    XMPP.subscribe(XMPP.jid(), self(), context());
  }

  public JID jid() {
    return bareJid;
  }

  @ActorMethod
  public void invoke(ConnStatus status) { // connection acquired
    log.fine("User agent " + bareJid + " received " + status);

    final ActorRef sender = sender();
    final String resource = status.resource;
    if (status.connected) {
      connectionManager.connected(resource, sender);
      messagesStash.forAwaitingDelivery(status, tellTo(sender));
      presenceTracker.forAvailable(tellTo(sender));
    }
    else {
      connectionManager.disconnected(resource, sender);
      // todo: should it just be a call to out()?
      invoke(notAvailable(resource));
    }
  }

  @ActorMethod
  public void invoke(final Presence presence) {
    log.fine("User agent " + bareJid + " received presence " + presence.from() + ": " + presence.available());

    if (isIncoming(presence)) {
      if (presenceTracker.updatePresence(presence)) {
        in(presence);
      }
    }
    else {
      out(presence);
    }
  }

  @ActorMethod
  public void invoke(final Iq iq) {
    log.fine("User agent " + bareJid + " received iq " + iq.from() + ": " + iq.get());

    if (isIncoming(iq)) {
      in(iq);
    }
    else {
      XMPP.send(iq, context());
    }
  }

  @ActorMethod
  public void invoke(final Message msg) {
    log.fine("User agent " + bareJid + " received message " + msg.from() + ": " + msg.id());

    messagesStash.persist(msg);
    if (isIncoming(msg)) {
      in(msg);
    }
    else {
      out(msg);
    }
  }

  private boolean isIncoming(final Stanza stanza) {
    return !jid().bareEq(stanza.from());
  }

  @NotNull
  private Presence notAvailable(final String resource) {
    return new Presence(new JID(jid().local(), jid().domain(), resource), false);
  }

  @NotNull
  private <T extends Stanza> Consumer<T> tellTo(final ActorRef recipient) {
    return stanza -> recipient.tell(stanza, self());
  }

  @ActorMethod
  public void invoke(final Delivered delivered) {
    messagesStash.persist(delivered);
  }

  private void out(Stanza stanza) {
    XMPP.send(stanza, context());
    // todo: looks like a hardcode?
    if ("expert".equals(stanza.from().resource())) {
      LaborExchange.Experts.tellTo(jid(), stanza, self(), context());
    }
    if (stanza instanceof Message) {
      notificationController.storeTokenIfPresent((Message) stanza);
    }
  }

  private void in(final Stanza stanza) {
    if (connectionManager.hasConnections()) {
      connectionManager.forConnected(conn -> conn.tell(stanza, self()));
    }
    else if (stanza instanceof Message) {
      notificationController.sendNotifications((Message) stanza);
    }
  }

  @Override
  public void onReceiveRecover(Object o) throws Exception {
    messagesStash.onReceiveRecover(o);
    if (o instanceof Message) {
      notificationController.storeTokenIfPresent((Message) o);
    }
  }

  @SuppressWarnings("UnusedParameters")
  @ActorMethod
  public void invoke(SaveSnapshotSuccess sss) {}

  @ActorMethod
  public void invoke(SaveSnapshotFailure ssf) {
    log.warning("Snapshot failed:" + ssf);
  }

  @Override
  public String persistenceId() {
    return bareJid.getAddr();
  }

  public static class NotificationController {
    private final Set<String> appTokens = new HashSet<>();

    public void sendNotifications(final Message message) {
      if (!appTokens.isEmpty()) {
        final NotificationsManager notificationsManager = NotificationsManager.instance();
        appTokens.stream().forEach(token -> notificationsManager.sendPush(message, token));
      }
    }

    public void storeTokenIfPresent(final Message message) {
      if (message.has(Operations.Token.class)) {
        appTokens.add(TokenUtil.sanitizeTokenString(message.get(Operations.Token.class).value()));
      }
    }

    public String[] appTokens() {
      return appTokens.toArray(new String[appTokens.size()]);
    }
  }

  // todo: look like XMPP.PresenceTracker is something similar, but no bare jids here
  public static class PresenceTracker {
    private final Map<JID, Presence> presenceMap = new HashMap<>();

    public boolean updatePresence(final Presence presence) {
      return !presence.equals(presenceMap.put(presence.from(), presence));
    }

    public void forAvailable(final Consumer<Presence> consumer) {
      presenceMap.values().stream()
        .filter(Presence::available)
        .forEach(consumer);
    }
  }

  public static class Delivered implements Serializable {
    final String id;
    final String resource;

    public Delivered(String id, String resource) {
      this.id = id;
      this.resource = resource;
    }

    @Override
    public String toString() {
      return "Delivered{" +
        "id='" + id + '\'' +
        ", resource='" + resource + '\'' +
        '}';
    }
  }

  public static class MessagesStash {
    private final UserAgent userAgent;

    private final List<Message> messages = new ArrayList<>();
    private final MultiMap<String, String> delivered = new MultiMap<>();

    public MessagesStash(final UserAgent userAgent) {
      this.userAgent = userAgent;
    }

    public void persist(Message message) {
      userAgent.persist(message, m -> messages.add(message));
    }

    public void persist(Delivered delivered) {
      userAgent.persist(delivered, d -> this.delivered.put(delivered.resource, delivered.id));
    }

    public void forAwaitingDelivery(final ConnStatus connStatus, final Consumer<Message> consumer) {
      final Collection<String> deliveredToResource = delivered.get(connStatus.resource);
      messages.stream()
        .filter(msg -> !deliveredToResource.contains(msg.id()))
        .filter(userAgent::isIncoming)
        .forEach(consumer);
    }

    public void onReceiveRecover(final Object o) {
      if (o instanceof Message) {
        final Message message = (Message) o;
        messages.add(message);
      }
      if (o instanceof Delivered) {
        final Delivered del = (Delivered) o;
        delivered.put(del.resource, del.id);
      }
    }
  }

  public static class ConnStatus {
    final boolean connected;
    final String resource;

    public ConnStatus(boolean connected, String resource) {
      this.connected = connected;
      this.resource = resource;
    }

    @Override
    public String toString() {
      return "ConnStatus{" +
        "connected=" + connected +
        ", resource='" + resource + '\'' +
        '}';
    }
  }

  public static class ConnectionManager {
    private final UserAgent userAgent;
    private final Map<String, ActorRef> connecters = new HashMap<>();

    public ConnectionManager(final UserAgent userAgent) {
      this.userAgent = userAgent;
    }

    public void connected(final String resource, final ActorRef sender) {
      final ActorRef existing = connecters.put(resource, sender);
      if (existing != null) {
        log.warning("Concurrent connectors [" + sender + ", " + existing + "] for the same resource: " + resource + " for " + userAgent.jid() + "!");
      }
    }

    public void disconnected(final String resource, final ActorRef sender) {
      connecters.remove(resource);
    }

    public boolean hasConnections() {
      return !connecters.isEmpty();
    }

    public void forConnected(final Consumer<ActorRef> consumer) {
      connecters.values().stream().forEach(consumer);
    }
  }
}
