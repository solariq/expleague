package com.tbts.server.agents;

import akka.actor.ActorRef;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.stanza.Iq;
import com.tbts.xmpp.stanza.Stanza;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 20:40
 */
public class UserAgent extends MailBoxAgent {
  private ActorRef connecter;
  private ActorRef role;

  public UserAgent(JID jid) {
    super(jid);
  }

  public void invoke(Connected connected) {
    connecter = connected.connecter;
    role = connected.role;
    for (int i = 0; i < undelivered.size(); i++) {
      final Stanza stanza = undelivered.get(i);
      connecter.tell(stanza, getSelf());
    }
    XMPP.subscribe(XMPP.jid(), self(), context());
  }

  public void invoke(Stanza stanza) {
    if (jid().bareEq(stanza.from())) { // outgoing
      XMPP.send(stanza, context());
      if (role != null && !(stanza instanceof Iq))
        role.forward(stanza, context());
    }
    else {
      if (connecter != null)
        connecter.tell(stanza, self());
    }
  }

  public static class Connected {
    private final ActorRef connecter;
    private final ActorRef role;

    public Connected(ActorRef connecter, ActorRef role) {
      this.connecter = connecter;
      this.role = role;
    }
  }
}
