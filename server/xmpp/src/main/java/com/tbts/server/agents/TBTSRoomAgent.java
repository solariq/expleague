package com.tbts.server.agents;

import com.tbts.model.handlers.Archive;
import com.tbts.modelNew.Offer;
import com.tbts.modelNew.Operations;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.Item;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Iq;
import com.tbts.xmpp.stanza.Message;
import com.tbts.xmpp.stanza.Message.MessageType;
import com.tbts.xmpp.stanza.Presence;
import com.tbts.xmpp.stanza.Stanza;

import java.util.*;

/**
 * User: solar
 * Date: 16.12.15
 * Time: 13:18
 */
@SuppressWarnings("UnusedParameters")
public class TBTSRoomAgent extends UntypedActorAdapter {
  private final List<Item> snapshot = new ArrayList<>();

  private final Set<JID> partisipants = new HashSet<>();
  private final Map<JID, Presence.Status> presence = new HashMap<>();
  private final JID jid;

  public TBTSRoomAgent(JID jid) {
    this.jid = jid;
    Archive.instance().visitMessages(jid.local(), new Archive.MessageVisitor() {
      @Override
      public boolean accept(String authorId, CharSequence message, long ts) {
        snapshot.add(Item.create(message));
        return true;
      }
    });
    if (snapshot.isEmpty())
      invoke(new Message(jid, null, MessageType.GROUP_CHAT, "Welcome to room " + jid));
  }

  public void invoke(Operations.Resume resume) {
    final Optional<Message> subject = snapshot.stream()
        .filter(s -> s instanceof Message).map(s -> (Message) s)
        .filter(m -> m.get(Message.Subject.class) != null)
        .findFirst();
    if (subject.isPresent()) {
      final Message msg = subject.get();
      LaborExchange.reference(context()).tell(new Offer(jid, msg.from(), msg.get(Message.Subject.class)), self());
    }
  }

  public void invoke(Message msg) {
    log(msg);
    enterRoom(msg.from());

    if (msg.type() == MessageType.GROUP_CHAT) { // broadcast
      for (final JID jid : partisipants) {
        if (jid.bareEq(msg.from()))
          continue;
        final Message copy = msg.copy();
        copy.to(jid);
        XMPP.send(copy, context());
      }
    }

    if (msg.get(Message.Subject.class) != null)
      LaborExchange.reference(context()).tell(new Offer(jid, msg.from(), msg.get(Message.Subject.class)), self());
  }

  public void invoke(Presence presence) {
    log(presence);
    final JID from = presence.from();
    enterRoom(from);
    final Presence.Status currentStatus = this.presence.get(from);
    if (presence.status().equals(currentStatus))
      return;
    this.presence.put(from, presence.status());
    for (final JID jid : partisipants) {
      final Presence copy = presence.copy();
      if (jid.bareEq(from))
        continue;
      copy.to(jid);
      XMPP.send(copy, context());
    }
  }

  private void enterRoom(JID jid) {
    if (jid.bareEq(this.jid))
      return;
    if (!partisipants.contains(jid.bare())) {
      partisipants.add(jid.bare());
      snapshot.stream().filter(s -> s instanceof Message && ((Message)s).type() == MessageType.GROUP_CHAT).map(s -> (Message)s).forEach(message -> {
        final Message copy = message.copy();
        copy.to(jid);
        XMPP.send(copy, context());
      });
    }
  }

  public void invoke(Iq command) {
    log(command);
    XMPP.send(Iq.answer(command), context());
    if (command.type() == Iq.IqType.SET)
      invoke(new Message(jid, null, MessageType.GROUP_CHAT, "Room set up and unlocked."));
  }

  public void log(Stanza stanza) { // saving everything to archive
//    System.out.println(stanza.xmlString());
    snapshot.add(stanza);
    Archive.instance().log(jid.local(), stanza.from().toString(), stanza.xmlString());
  }

  public void invoke(View type) {
    switch(type) {
      case ALL:
        sender().tell(Collections.unmodifiableList(snapshot), self());
        break;
    }
  }

  public enum View {
    ALL
  }
}
