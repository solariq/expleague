package com.tbts.server.xmpp.agents;

import akka.actor.ActorRef;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Stanza;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 20:40
 */
public class UserAgent extends MailBoxAgent {
  private ActorRef connecter;

  public UserAgent(JID jid) {
    super(jid);
  }

  public void invoke(Connected connected) {
    connecter = connected.connecter;
    for (int i = 0; i < undelivered.size(); i++) {
      final Stanza stanza = undelivered.get(i);
      connecter.tell(stanza, getSelf());
    }
  }

  public void invoke(Stanza stanza) {
    if (connecter != null)
      connecter.tell(stanza, self());
  }

  public static class Connected {
    private final ActorRef connecter;

    public Connected(ActorRef connecter) {
      this.connecter = connecter;
    }
  }
}
