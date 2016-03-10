package com.expleague.server.xmpp.phase;

import akka.actor.ActorRef;
import com.expleague.model.Delivered;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.Roster;
import com.expleague.server.XMPPDevice;
import com.expleague.server.agents.UserAgent;
import com.expleague.server.agents.XMPP;
import com.expleague.server.services.XMPPServices;
import com.expleague.xmpp.Features;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.Bind;
import com.expleague.xmpp.control.Close;
import com.expleague.xmpp.control.Session;
import com.expleague.xmpp.control.receipts.Received;
import com.expleague.xmpp.control.receipts.Request;
import com.expleague.xmpp.stanza.Iq;
import com.expleague.xmpp.stanza.Message;
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
      tryRequestMessageReceipt(msg);
      answer(msg);
    }
    else { // outgoing
      tryProcessMessageReceipt(msg);
      if (!isDeliveryReceipt(msg)) {
        msg.from(jid);
        if (agent != null)
          agent.tell(msg, self());
      }
    }
  }

  protected void tryRequestMessageReceipt(final Stanza msg) {
    if (!(msg instanceof Message)) {
      return;
    }

    final Message message = (Message) msg;
    if (!message.has(Received.class) && !message.has(Request.class)) {
      message.append(new Request());
    }
  }

  protected void tryProcessMessageReceipt(final Stanza msg) {
    if (!(msg instanceof Message)) {
      return;
    }

    final Message message = (Message) msg;
    if (message.has(Received.class)) {
      final String messageId = message.get(Received.class).getId();
      log.info("Client received: " + messageId);
      if (courier != null) {
        courier.tell(new Delivered(messageId, jid.resource()), self());
      }
      else {
        log.warning("Can't process delivery ack to " + jid + ", courier is absent");
      }
    }
    else if (message.has(Request.class)) {
      final Message ack = new Message(message.to(), message.from());
      final String messageId = message.id();
      ack.append(new Received(messageId));
      log.info("Server received: " + messageId);
      answer(ack);
    }
  }

  protected boolean isDeliveryReceipt(final Stanza stanza) {
    return stanza instanceof Message && ((Message) stanza).has(Received.class);
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
