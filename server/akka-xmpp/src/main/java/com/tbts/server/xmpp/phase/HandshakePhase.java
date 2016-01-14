package com.tbts.server.xmpp.phase;

import akka.actor.ActorRef;
import com.tbts.server.xmpp.XMPPClientConnection;
import com.tbts.xmpp.Features;
import com.tbts.xmpp.control.tls.Proceed;
import com.tbts.xmpp.control.tls.StartTLS;

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
