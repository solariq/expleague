package com.expleague.server.xmpp.phase;

import akka.actor.ActorRef;
import com.expleague.model.Delivered;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.agents.UserAgent;
import com.expleague.server.agents.XMPP;
import com.expleague.server.services.XMPPServices;
import com.expleague.xmpp.Features;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.Bind;
import com.expleague.xmpp.control.Close;
import com.expleague.xmpp.control.Session;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 16:37
 */
public class ConnectedPhase extends XMPPPhase {
  private JID jid;
  private boolean bound = false;
  private ActorRef agent;

  public ConnectedPhase(ActorRef connection, String authId) {
    super(connection);
    this.jid = JID.parse(authId + "@" + ExpLeagueServer.config().domain());
  }

  public void open() {
    answer(new Features(new Bind(), new Session()));
  }

  public void invoke(Iq<?> iq) {
    if (jid().equals(iq.to())) { // incoming
      answer(iq);
      return;
    }
    if (bound)
      iq.from(jid());
    switch (iq.type()) {
      case SET : {
        if (iq.get() instanceof Bind) {
          bound = true;
          jid = jid.resource(((Bind) iq.get()).resource());
          answer(Iq.answer(iq, new Bind(jid())));
          break;
        }
        else if (iq.get() instanceof Session) {
          bound = true;
          agent = XMPP.register(jid().bare(), context());
          agent.tell(new UserAgent.ConnStatus(true, jid.resource()), self());
          answer(Iq.answer(iq, new Session()));
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

  @Override
  public void postStop() throws Exception {
    if (agent != null) {
      agent.tell(new UserAgent.ConnStatus(false, jid.resource()), self());
    }
  }

  public void invoke(Stanza msg) {
    if (msg instanceof Iq)
      return;
    if (msg.to() != null && jid().bareEq(msg.to())) { // incoming
      answer(msg);
      if (!(msg instanceof Presence))
        sender().tell(new Delivered(msg.id(), jid.resource()), self());
    }
    else { // outgoing
      msg.from(jid);
      if (agent != null)
        agent.tell(msg, self());
    }
  }

  @SuppressWarnings("UnusedParameters")
  public void invoke(Close close) throws Exception {
    if (agent != null) {
      agent.tell(new Presence(jid, false), self());
      agent.tell(new UserAgent.ConnStatus(false, jid.resource()), self());
    }
  }

  public JID jid() {
    return jid;
  }
}
