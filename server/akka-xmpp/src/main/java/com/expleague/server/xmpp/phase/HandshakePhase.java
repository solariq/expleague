package com.expleague.server.xmpp.phase;

import akka.actor.ActorRef;
import com.expleague.server.xmpp.XMPPClientConnection;
import com.expleague.xmpp.Features;
import com.expleague.xmpp.control.tls.Proceed;
import com.expleague.xmpp.control.tls.StartTLS;

/**
 * User: solar
 * Date: 09.12.15
 * Time: 14:38
 */
public class HandshakePhase extends XMPPPhase {

  protected HandshakePhase(ActorRef connection) {
    super(connection);
  }

  @SuppressWarnings("UnusedParameters")
  public void invoke(StartTLS tls) {
    last(new Proceed(), XMPPClientConnection.ConnectionState.STARTTLS);
  }

  @Override
  public void open() {
    answer(new Features(new StartTLS()));
  }
}
