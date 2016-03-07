package com.expleague.server.xmpp.phase;

import akka.actor.ActorRef;
import com.expleague.model.Delivered;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.Roster;
import com.expleague.server.XMPPDevice;
import com.expleague.server.agents.UserAgent;
import com.expleague.server.agents.XMPP;
import com.expleague.server.services.XMPPServices;
import com.expleague.server.xmpp.XMPPClientConnection;
import com.expleague.xmpp.Features;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.Bind;
import com.expleague.xmpp.control.Close;
import com.expleague.xmpp.control.Session;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Presence;
import com.expleague.xmpp.stanza.Stanza;

import java.util.logging.Logger;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 16:37
 */
public class ConnectedPhase extends XMPPPhase {
  private static final Logger log = Logger.getLogger(ConnectedPhase.class.getName());

  private JID jid;
  private boolean bound = false;
  private ActorRef agent;
  private ActorRef courier;
  private XMPPDevice device;

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
        final Object payload = iq.get();
        if (payload instanceof Bind) {
          bound = true;
          device = Roster.instance().device(jid.local());
          final String resource = ((Bind) payload).resource();
          jid = device.user().jid().resource(device.name() + (resource.isEmpty() ? "" : "/" + resource));
          answer(Iq.answer(iq, new Bind(jid())));
          break;
        }
        else if (payload instanceof Session) {
          bound = true;
          agent = XMPP.register(jid().bare(), context());
          agent.tell(new UserAgent.ConnStatus(true, jid.resource(), device), self());
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
      agent.tell(new UserAgent.ConnStatus(false, jid.resource(), device), self());
    }
  }

  public void invoke(ActorRef courier) {
    this.courier = courier;
  }

  public void invoke(Stanza msg) {
    if (msg instanceof Iq)
      return;
    if (msg.to() != null && jid().bareEq(msg.to())) { // incoming
      answer(msg);
    }
    else { // outgoing
      msg.from(jid);
      if (agent != null)
        agent.tell(msg, self());
    }
  }

  public void invoke(XMPPClientConnection.DeliveryAck ack) {
    if (courier != null) {
      courier.tell(new Delivered(ack.getId(), jid.resource()), self());
    }
    else {
      log.warning("Can't process delivery ack to " + jid + ", courier is absent");
    }
  }

  @SuppressWarnings("UnusedParameters")
  public void invoke(Close close) throws Exception {
    if (agent != null) {
      agent.tell(new Presence(jid, false), self());
      agent.tell(new UserAgent.ConnStatus(false, jid.resource(), device), self());
    }
  }

  public JID jid() {
    return jid;
  }
}
