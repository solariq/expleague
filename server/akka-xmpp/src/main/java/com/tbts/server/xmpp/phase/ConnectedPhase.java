package com.tbts.server.xmpp.phase;

import akka.actor.ActorRef;
import com.tbts.server.TBTSServer;
import com.tbts.server.agents.LaborExchange;
import com.tbts.server.agents.MailBoxAgent;
import com.tbts.server.agents.UserAgent;
import com.tbts.server.agents.XMPP;
import com.tbts.server.services.XMPPServices;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.Features;
import com.tbts.xmpp.JID;
import com.tbts.xmpp.control.Bind;
import com.tbts.xmpp.control.Close;
import com.tbts.xmpp.control.Open;
import com.tbts.xmpp.control.Session;
import com.tbts.xmpp.stanza.Iq;
import com.tbts.xmpp.stanza.Presence;
import com.tbts.xmpp.stanza.Stanza;

import java.util.logging.Logger;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 16:37
 */
public class ConnectedPhase extends UntypedActorAdapter {
  private static final Logger log = Logger.getLogger(ConnectedPhase.class.getName());
  private JID jid;
  private final ActorRef outFlow;
  private boolean bound = false;
  private ActorRef agent;

  public ConnectedPhase(String authId, ActorRef outFlow) {
    this.jid = JID.parse(authId + "@" + TBTSServer.config().domain());
    this.outFlow = outFlow;
  }

  public void invoke(Open o) {
    outFlow.tell(new Features(new Bind(), new Session()), self());
  }

  public void invoke(Iq<?> iq) {
    if (jid().equals(iq.to())) { // incoming
      outFlow.tell(iq, getSender());
      return;
    }
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
          bound = true;
          agent = XMPP.register(jid(), context());
          final ActorRef role;
          switch (jid().resource()) {
            case "expert":
              role = LaborExchange.registerExpert(jid.bare(), context());
              break;
            default:
              role = null;
          }
          agent.tell(new UserAgent.Connected(self(), role), self());
          outFlow.tell(Iq.answer(iq, new Session()), self());
          break;
        }
      }
      default:
        if (iq.to() != null) {
          iq.from(jid);
          agent.tell(iq, self());
        }
        else XMPPServices.reference(context().system()).tell(iq, self());
    }
  }

  public void invoke(Stanza msg) {
    if (msg instanceof Iq)
      return;
    if (msg.to() != null && jid().bareEq(msg.to())) { // incoming
      outFlow.tell(msg, sender());
      if (sender().path().name().equals(jid.bare().toString())) // TODO: remove this shit
        sender().tell(new MailBoxAgent.Delivered(msg.id()), self());
      else
        log.warning("Message " + msg + " received from strange source: " + sender().path());
    }
    else { // outgoing
      msg.from(jid);
      if (agent != null)
        agent.tell(msg, self());
    }
  }

  @SuppressWarnings("UnusedParameters")
  public void invoke(Close close) throws Exception {
    if (agent != null)
    agent.tell(new Presence(jid, false), self());
  }

  public JID jid() {
    return jid;
  }
}
