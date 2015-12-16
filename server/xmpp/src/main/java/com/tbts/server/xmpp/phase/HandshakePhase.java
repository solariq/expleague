package com.tbts.server.xmpp.phase;

import com.tbts.xmpp.Features;
import com.tbts.xmpp.control.tls.Proceed;
import com.tbts.xmpp.control.tls.StartTLS;

/**
 * User: solar
 * Date: 09.12.15
 * Time: 14:38
 */
public class HandshakePhase extends XMPPPhase {

  public HandshakePhase() {
    answer(new Features(new StartTLS()));
  }

  @SuppressWarnings("UnusedParameters")
  public void invoke(StartTLS tls) {
    answer(new Proceed());
    stop();
  }
}
