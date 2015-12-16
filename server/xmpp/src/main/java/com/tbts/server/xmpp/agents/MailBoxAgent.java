package com.tbts.server.xmpp.agents;

import akka.persistence.SnapshotOffer;
import akka.persistence.UntypedPersistentActor;
import com.spbsu.commons.system.RuntimeUtils;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Stanza;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 20:02
 */
public abstract class MailBoxAgent extends UntypedPersistentActor {
  private final JID bareJid;
  protected final List<Stanza> undelivered = new ArrayList<>();
  private final RuntimeUtils.InvokeDispatcher dispatcher;

  public MailBoxAgent(JID jid) {
    dispatcher = new RuntimeUtils.InvokeDispatcher(getClass(), this::unhandled);
    this.bareJid = jid.bare();
  }

  @Override
  public void onReceiveRecover(Object msg) throws Exception {
    if (msg instanceof Stanza) {
      this.persistAsync((Stanza)msg, undelivered::add);
    }
    else if (msg instanceof SnapshotOffer) {
      //noinspection unchecked
      undelivered.addAll((List<Stanza>) ((SnapshotOffer) msg).snapshot());
    }
    else unhandled(msg);
  }

  @Override
  public void onReceiveCommand(Object msg) throws Exception {
    if (msg instanceof Stanza) {
      dispatcher.invoke(this, msg);
      undelivered.add((Stanza) msg);
    }
    else if (msg instanceof Delivered) {
      final Iterator<Stanza> iterator = undelivered.iterator();
      final String id = ((Delivered) msg).id;
      while (iterator.hasNext()) {
        if (id.equals(iterator.next().id())) {
          iterator.remove();
          break;
        }
      }
    }
    else dispatcher.invoke(this, msg);
  }

  @Override
  public String persistenceId() {
    return bareJid.getAddr();
  }

  public static class Delivered {
    private final String id;
    public Delivered(String id) {
      this.id = id;
    }
  }

  public JID jid() {
    return bareJid;
  }
}
