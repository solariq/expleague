package com.tbts.server.agents;

import com.tbts.modelNew.Offer;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Iq;
import com.tbts.xmpp.stanza.Message;
import com.tbts.xmpp.stanza.Message.MessageType;
import com.tbts.xmpp.stanza.Presence;

import java.util.HashSet;
import java.util.Set;

/**
 * User: solar
 * Date: 16.12.15
 * Time: 13:18
 */
public class RoomAgent extends MailBoxAgent {
  private final Set<JID> partisipants = new HashSet<>();
  public RoomAgent(JID jid) {
    super(jid);
  }

  public void invoke(Message msg) {
    super.invoke(msg);

    for (final JID jid : partisipants) {
      if (jid.bareEq(msg.from()))
        continue;
      final Message copy = msg.copy();
      copy.type(MessageType.GROUP_CHAT);
      copy.to(jid);
      XMPP.send(copy, context());
    }

    if (msg.get(Message.Subject.class) != null) {
      LaborExchange.reference(context()).tell(new Offer(jid(), msg.from(), msg.get(Message.Subject.class)), self());
    }
  }

  public void invoke(Presence presence) {
    if (!partisipants.contains(presence.from().bare())) {
      if (partisipants.isEmpty())
        undelivered.add(new Message(jid(), null, "Welcome to room " + jid()));
      partisipants.add(presence.from().bare());
      for (final Message stanza : undelivered) {
        final Message copy = stanza.copy();
        copy.type(MessageType.GROUP_CHAT);
        copy.to(presence.from());
        XMPP.send(copy, context());
      }
    }

    for (final JID jid : partisipants) {
      final Presence copy = presence.copy();
      if (jid.bareEq(presence.from()))
        continue;
      copy.to(jid);
      XMPP.send(copy, context());
    }
  }

  public void invoke(Iq command) {
    XMPP.send(Iq.answer(command), context());
    if (command.type() == Iq.IqType.SET)
      invoke(new Message(jid(), null, "Room set up and unlocked."));
  }
}
