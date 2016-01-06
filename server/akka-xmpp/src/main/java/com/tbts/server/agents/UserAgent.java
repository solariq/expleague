package com.tbts.server.agents;

import akka.actor.ActorRef;
import akka.persistence.SaveSnapshotFailure;
import akka.persistence.SaveSnapshotSuccess;
import akka.persistence.UntypedPersistentActor;
import com.spbsu.commons.system.RuntimeUtils;
import com.spbsu.commons.util.MultiMap;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Iq;
import com.tbts.xmpp.stanza.Message;
import com.tbts.xmpp.stanza.Presence;
import com.tbts.xmpp.stanza.Stanza;

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
  private ActorRef expert;

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
      if ("expert".equals(resource))
        expert = LaborExchange.registerExpert(jid(), context());
      final Collection<String> deliveredToResource = delivered.get(resource);
      messages.stream()
          .filter(msg -> !deliveredToResource.contains(msg.id()))
          .forEach(msg -> sender().tell(msg, self()));
      presenceMap.values().stream()
          .filter(Presence::available)
          .forEach(p -> sender().tell(p, self()));
    }
    else connecters.remove(resource);
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
    if (!msg.from().bareEq(jid())) { // incoming
      this.persist(msg, messages::add);
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
    if(expert != null)
      expert.tell(stanza, self());
  }

  private void in(Stanza stanza) {
    connecters.values().stream().forEach(conn -> conn.tell(stanza, self()));
  }

  @Override
  public void onReceiveRecover(Object o) throws Exception {
    if (o instanceof Message) {
      messages.add((Message)o);
    }
    if (o instanceof Delivered) {
      final Delivered del = (Delivered) o;
      delivered.put(del.resource, del.id);
    }
  }

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
