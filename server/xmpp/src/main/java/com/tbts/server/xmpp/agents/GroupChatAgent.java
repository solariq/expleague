package com.tbts.server.xmpp.agents;

import akka.actor.ActorSelection;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Iq;
import com.tbts.xmpp.stanza.Message;
import com.tbts.xmpp.stanza.Presence;
import com.tbts.xmpp.stanza.Stanza;

import java.util.HashSet;
import java.util.Set;

/**
 * User: solar
 * Date: 16.12.15
 * Time: 13:18
 */
public class GroupChatAgent extends MailBoxAgent {
  private final Set<JID> partisipants = new HashSet<>();
  public GroupChatAgent(JID jid) {
    super(jid);
  }

  public void invoke(Message msg) {
    final ActorSelection agency = context().actorSelection("/user/xmpp");

    for (final JID jid : partisipants) {
      if (jid.bareEq(msg.from()))
        continue;
      final Message copy = msg.copy();
      copy.type(Stanza.StanzaType.GROUP_CHAT);
      copy.to(jid);
      agency.tell(copy, self());
    }
  }

  public void invoke(Presence presence) {
    final ActorSelection agency = context().actorSelection("/user/xmpp");
    if (!partisipants.contains(presence.from().bare())) {
      if (partisipants.isEmpty()) {
        undelivered.add(new Message(jid(), null, "Welcome to room " + jid()));
      }
      partisipants.add(presence.from().bare());
      for (final Stanza stanza : undelivered) {
        final Stanza copy = stanza.copy();
        copy.type(Stanza.StanzaType.GROUP_CHAT);
        copy.to(presence.from());
        agency.tell(copy, self());
      }
    }

    for (final JID jid : partisipants) {
      final Presence copy = presence.copy();
      if (jid.bareEq(presence.from()))
        continue;
      copy.to(jid);
      agency.tell(copy, self());
    }
  }

  public void invoke(Iq command) {
    context().actorSelection("/user/xmpp").tell(Iq.answer(command), self());
    if (command.type() == Stanza.StanzaType.SET) {
      invoke(new Message(jid(), null, "Room set up and unlocked."));
    }
  }
}
