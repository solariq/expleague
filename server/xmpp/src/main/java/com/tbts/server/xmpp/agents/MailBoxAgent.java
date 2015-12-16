package com.tbts.server.xmpp.agents;

import akka.actor.ActorRef;
import akka.persistence.SnapshotOffer;
import akka.persistence.UntypedPersistentActor;
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
public class MailBoxAgent extends UntypedPersistentActor {
  private final JID bareJid;
  private ActorRef connecter;
  private final List<Stanza> undelivered = new ArrayList<>();

  public MailBoxAgent(JID jid) {
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
      undelivered.add((Stanza) msg);
      if (connecter != null)
        connecter.tell(msg, getSelf());
    }
    else if (msg instanceof Connected) {
      connecter = ((Connected) msg).connecter;
      for (int i = 0; i < undelivered.size(); i++) {
        final Stanza stanza = undelivered.get(i);
        connecter.tell(stanza, getSelf());
      }
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
    else unhandled(msg);
  }

  @Override
  public String persistenceId() {
    return bareJid.getAddr();
  }

  public static class Connected {
    private final ActorRef connecter;

    public Connected(ActorRef connecter) {
      this.connecter = connecter;
    }
  }

  public static class Delivered {
    private final String id;
    public Delivered(String id) {
      this.id = id;
    }
  }
}
