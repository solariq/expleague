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
  private final ActorRef controller;

  public HandshakePhase(ActorRef controller) {
    this.controller = controller;
    answer(new Features(new StartTLS()));
  }

  @SuppressWarnings("UnusedParameters")
  public void invoke(StartTLS tls) {
    answer(new Proceed());
    stop();
    controller.tell(XMPPClientConnection.ConnectionState.AUTHORIZATION, ActorRef.noSender());
  }
}
