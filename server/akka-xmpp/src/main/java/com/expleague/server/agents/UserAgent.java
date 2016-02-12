package com.expleague.server.agents;

import akka.actor.ActorRef;
import akka.persistence.SaveSnapshotFailure;
import akka.persistence.SaveSnapshotSuccess;
import akka.persistence.UntypedPersistentActor;
import com.relayrides.pushy.apns.util.TokenUtil;
import com.spbsu.commons.system.RuntimeUtils;
import com.spbsu.commons.util.MultiMap;
import com.expleague.model.Operations;
import com.expleague.util.ios.NotificationsManager;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;

import java.io.Serializable;
import java.util.*;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 20:40
 */
public class UserAgent extends UntypedPersistentActor {
  private static final Logger log = Logger.getLogger(UserAgent.class.getName());
  private final JID bareJid;
  private final List<Message> messages = new ArrayList<>();
  private final MultiMap<String, String> delivered = new MultiMap<>();
  private final Map<JID, Presence> presenceMap = new HashMap<>();
  private final RuntimeUtils.InvokeDispatcher dispatcher;

  public UserAgent(JID jid) {
    dispatcher = new RuntimeUtils.InvokeDispatcher(getClass(), this::unhandled);
    XMPP.subscribe(XMPP.jid(), self(), context());
    this.bareJid = jid.bare();
  }

  public JID jid() {
    return bareJid;
  }
  private Map<String, ActorRef> connecters = new HashMap<>();

  public static class ConnStatus {
    final boolean connected;
    final String resource;

    public ConnStatus(boolean connected, String resource) {
      this.connected = connected;
      this.resource = resource;
    }
  }

  public void invoke(ConnStatus status) { // connection acquired
    final String resource = status.resource;
    if (status.connected) {
      final ActorRef put = connecters.put(resource, sender());
      if (put != null)
        log.warning("Concurrent connectors for the same resource: " + resource + " for " + jid() + "!");
      final Collection<String> deliveredToResource = delivered.get(resource);
      messages.stream()
          .filter(msg -> !deliveredToResource.contains(msg.id()))
          .forEach(msg -> {
            if (!msg.from().bareEq(jid()))
              sender().tell(msg, self());
          });
      presenceMap.values().stream()
          .filter(Presence::available)
          .forEach(p -> sender().tell(p, self()));
    }
    else {
      connecters.remove(resource);
      invoke(new Presence(new JID(jid().local(), jid().domain(), resource), false));
    }
  }

  public void invoke(final Presence presence) {
    if (!presence.from().bareEq(jid())) { // incoming
      final Presence replace = presenceMap.put(presence.from(), presence);
      if (!presence.equals(replace))
        in(presence);
    }
    else out(presence);
  }

  public void invoke(final Iq iq) {
    if (iq.from().bareEq(jid()))
      XMPP.send(iq, context());
    else
      in(iq);
  }

  public void invoke(final Message msg) {
    this.persist(msg, messages::add);
    if (!msg.from().bareEq(jid())) { // incoming
      in(msg);
    }
    else out(msg);
  }

  public static class Delivered implements Serializable {
    final String id;
    final String resource;


    public Delivered(String id, String resource) {
      this.id = id;
      this.resource = resource;
    }
  }

  public void invoke(final Delivered delivered) {
    this.persist(delivered, d -> this.delivered.put(delivered.resource, delivered.id));
  }

  private void out(Stanza stanza) {
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

  private void in(Stanza stanza) {
    connecters.values().stream().forEach(conn -> conn.tell(stanza, self()));
    if (connecters.isEmpty() && stanza instanceof Message) {
      appTokens.stream().forEach(
          token -> NotificationsManager.instance().sendPush((Message)stanza, token)
      );
    }
  }

  @Override
  public void onReceiveRecover(Object o) throws Exception {
    if (o instanceof Message) {
      final Message message = (Message) o;
      messages.add(message);
      if (message.has(Operations.Token.class)) {
        appTokens.add(TokenUtil.sanitizeTokenString(message.get(Operations.Token.class).value()));
      }
    }
    if (o instanceof Delivered) {
      final Delivered del = (Delivered) o;
      delivered.put(del.resource, del.id);
    }
  }

  private Set<String> appTokens = new HashSet<>();
  public String[] appTokens() {
    return appTokens.toArray(new String[appTokens.size()]);
  }

  @SuppressWarnings("UnusedParameters")
  public void invoke(SaveSnapshotSuccess sss) {}

  public void invoke(SaveSnapshotFailure ssf) {
    log.warning("Snapshot failed:" + ssf);
  }

  @Override
  public void onReceiveCommand(Object msg) throws Exception {
    dispatcher.invoke(this, msg);
  }

  @Override
  public String persistenceId() {
    return bareJid.getAddr();
  }
}
