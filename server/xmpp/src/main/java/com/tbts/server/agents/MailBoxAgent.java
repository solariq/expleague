package com.tbts.server.agents;

import akka.persistence.SnapshotOffer;
import akka.persistence.UntypedPersistentActor;
import com.spbsu.commons.system.RuntimeUtils;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Message;

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
  protected final List<Message> undelivered = new ArrayList<>();
  private final RuntimeUtils.InvokeDispatcher dispatcher;

  public MailBoxAgent(JID jid) {
    dispatcher = new RuntimeUtils.InvokeDispatcher(getClass(), this::unhandled);
    this.bareJid = jid.bare();
  }

  public void invoke(Message msg) {
    undelivered.add(msg);
  }

  public void invoke(Delivered delivered) {
    final Iterator<Message> iterator = undelivered.iterator();
    while (iterator.hasNext()) {
      if (delivered.id.equals(iterator.next().id())) {
        iterator.remove();
        break;
      }
    }
  }

  @Override
  public void onReceiveRecover(Object msg) throws Exception {
    if (msg instanceof Message) {
      this.persistAsync((Message)msg, undelivered::add);
    }
    else if (msg instanceof SnapshotOffer) {
      //noinspection unchecked
      undelivered.addAll((List<Message>) ((SnapshotOffer) msg).snapshot());
    }
    else unhandled(msg);
  }

  @Override
  public void onReceiveCommand(Object msg) throws Exception {
    dispatcher.invoke(this, msg);
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
