package com.tbts.server.agents;

import akka.persistence.SaveSnapshotSuccess;
import akka.persistence.SnapshotOffer;
import akka.persistence.UntypedPersistentActor;
import com.spbsu.commons.system.RuntimeUtils;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Message;
import com.tbts.xmpp.stanza.Presence;
import com.tbts.xmpp.stanza.Stanza;

import java.io.Serializable;
import java.util.*;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 20:02
 */
public abstract class MailBoxAgent extends UntypedPersistentActor {
  public static final int MESSAGES_IN_QUEUE = 10000;
  private final JID bareJid;
  protected final List<Stanza> undelivered = new ArrayList<>();
  protected final Map<JID, Presence> presenceMap = new HashMap<>();
  private final RuntimeUtils.InvokeDispatcher dispatcher;
  private int msgToSnapshot = MESSAGES_IN_QUEUE;

  public MailBoxAgent(JID jid) {
    dispatcher = new RuntimeUtils.InvokeDispatcher(getClass(), this::unhandled);
    this.bareJid = jid.bare();
  }

  public void invoke(Message msg) {
    this.persistAsync(msg, undelivered::add);
    if (msgToSnapshot-- <= 0)
      self().tell(new Snapshot(), self());
  }

  public void invoke(Presence presence) {
    this.persistAsync(presence, undelivered::add);
    final Presence replace = presenceMap.replace(presence.from(), presence);
    if (replace != null)
      invoke(new Delivered(replace.id()));
    if (msgToSnapshot-- <= 0)
      self().tell(new Snapshot(), self());
  }

  public void invoke(Delivered delivered) {
    this.persistAsync(delivered, d -> {
      final String id = d.id;
      removeById(id);
      if (msgToSnapshot-- <= 0)
        self().tell(new Snapshot(), self());
    });
  }

  private void removeById(String id) {
    final Iterator<Stanza> iterator = undelivered.iterator();
    while (iterator.hasNext()) {
      if (id.equals(iterator.next().id())) {
        iterator.remove();
        break;
      }
    }
  }

  @SuppressWarnings("UnusedParameters")
  public void invoke(Snapshot ignore) {
    this.saveSnapshot(new ArrayList<>(undelivered));
    msgToSnapshot = MESSAGES_IN_QUEUE;
  }

  @Override
  public void onReceiveRecover(Object msg) throws Exception {
    if (msg instanceof Stanza) {
      undelivered.add((Stanza) msg);
    }
    else if (msg instanceof Delivered) {
      removeById(((Delivered) msg).id);
    }
    else if (msg instanceof SnapshotOffer) {
      //noinspection unchecked
      undelivered.addAll((List<Stanza>) ((SnapshotOffer) msg).snapshot());
    }
    else unhandled(msg);
  }

  public void invoke(SaveSnapshotSuccess sss) {}
//  public void invoke(SaveSnapshotFailure sss) {}

  @Override
  public void onReceiveCommand(Object msg) throws Exception {
    dispatcher.invoke(this, msg);
  }

  @Override
  public String persistenceId() {
    return bareJid.getAddr();
  }

  public static class Delivered implements Serializable {
    private final String id;
    public Delivered(String id) {
      this.id = id;
    }
  }

  public static class Snapshot {}

  public JID jid() {
    return bareJid;
  }
}
