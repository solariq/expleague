package com.tbts.server.agents;

import com.tbts.modelNew.Offer;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Iq;
import com.tbts.xmpp.stanza.Message;
import com.tbts.xmpp.stanza.Message.MessageType;
import com.tbts.xmpp.stanza.Presence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: solar
 * Date: 16.12.15
 * Time: 13:18
 */
public class RoomAgent extends MailBoxAgent {
  private final Set<JID> partisipants = new HashSet<>();
  private final Map<JID, Presence.Status> presence = new HashMap<>();
  public RoomAgent(JID jid) {
    super(jid);
    undelivered.add(new Message(jid, null, MessageType.GROUP_CHAT, "Welcome to room " + jid));
  }

  public void invoke(Message msg) {
    super.invoke(msg);
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

    if (msg.get(Message.Subject.class) != null) {
      LaborExchange.reference(context()).tell(new Offer(jid(), msg.from(), msg.get(Message.Subject.class)), self());
    }
  }

  public void invoke(Presence presence) {
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
    if (jid.bareEq(jid()))
      return;
    if (!partisipants.contains(jid.bare())) {
      partisipants.add(jid.bare());
      for (final Message stanza : undelivered) {
        if (stanza.type() != MessageType.GROUP_CHAT)
          continue;
        final Message copy = stanza.copy();
        copy.to(jid);
        XMPP.send(copy, context());
      }
    }
  }

  public void invoke(Iq command) {
    XMPP.send(Iq.answer(command), context());
    if (command.type() == Iq.IqType.SET)
      invoke(new Message(jid(), null, MessageType.GROUP_CHAT, "Room set up and unlocked."));
  }
}
