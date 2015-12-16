package com.tbts.server.xmpp.phase;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import com.tbts.server.XMPPServer;
import com.tbts.server.services.Services;
import com.tbts.server.xmpp.agents.MailBoxAgent;
import com.tbts.server.xmpp.agents.UserAgent;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.Features;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.control.Bind;
import com.tbts.xmpp.control.Open;
import com.tbts.xmpp.control.Session;
import com.tbts.xmpp.stanza.Iq;
import com.tbts.xmpp.stanza.Stanza;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 16:37
 */
public class ConnectedPhase extends UntypedActorAdapter {
  private JID jid;
  private final ActorRef outFlow;
  private boolean bound = false;

  public ConnectedPhase(String authId, ActorRef outFlow) {
    this.jid = JID.parse(authId + "@" + XMPPServer.config().domain());
    this.outFlow = outFlow;
  }

  public void invoke(Open o) {
    outFlow.tell(new Features(new Bind(), new Session()), getSelf());
  }

  public void invoke(ActorRef agent) {
    agent.tell(new UserAgent.Connected(getSelf()), getSelf());
  }

  public void invoke(Iq<?> iq) {
    if (jid().equals(iq.to())) // skip outgoing iq
      return;
    if (bound)
      iq.from(jid());
    switch (iq.type()) {
      case SET : {
        if (iq.get() instanceof Bind) {
          bound = true;
          jid = jid.resource(((Bind) iq.get()).resource());
          outFlow.tell(Iq.answer(iq, new Bind(jid())), getSelf());
          break;
        }
        else if (iq.get() instanceof Session) {
          final ActorSelection agency = getContext().actorSelection("/user/xmpp");
          agency.tell(jid(), getSelf());
          outFlow.tell(Iq.answer(iq, new Session()), getSelf());
          break;
        }
      }
      default:
        Services.reference(getContext().system()).tell(iq, getSelf());
    }
  }

  public void invoke(Stanza msg) {
    if (msg instanceof Iq)
      return;
    if (msg.to() != null && jid().bareEq(msg.to())) { // incoming
      outFlow.tell(msg, getSender());
      getSender().tell(new MailBoxAgent.Delivered(msg.id()), getSelf());
    }
    else { // outgoing
      msg.from(jid);
      final ActorSelection agency = getContext().actorSelection("/user/xmpp");
      agency.tell(msg, getSelf());
    }
  }

  public JID jid() {
    return jid;
  }
}
