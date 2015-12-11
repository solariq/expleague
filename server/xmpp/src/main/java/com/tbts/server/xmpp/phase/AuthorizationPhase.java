package com.tbts.server.xmpp.phase;

import akka.actor.ActorRef;
import com.tbts.server.xmpp.XMPPClientConnection;
import com.tbts.xmpp.Features;
import com.tbts.xmpp.control.sasl.*;

import javax.security.sasl.AuthenticationException;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 10.12.15
 * Time: 16:03
 */
public class AuthorizationPhase extends XMPPPhase {
  private static final Logger log = Logger.getLogger(AuthorizationPhase.class.getName());
  private final ActorRef controller;
  private final Mechanisms auth;
  private SaslServer sasl;

  public AuthorizationPhase(ActorRef controller) {
    this.controller = controller;
    auth = new Mechanisms();
    auth.fillKnownMechanisms();
    answer(new Features(
        auth
    ));
  }

  public void invoke(Response response) {
    if (sasl == null)
      throw new IllegalStateException();
    try {
      if (response.data().length > 0 || !sasl.isComplete()) {
        final byte[] bytes = sasl.evaluateResponse(response.data());
        answer(new Challenge(bytes));
      }
      else {
        answer(new Success());
        controller.tell(XMPPClientConnection.ConnectionState.CONNECTED, ActorRef.noSender());
      }
    }
    catch (SaslException e) {
      if (e.getCause() instanceof AuthenticationException) {
        answer(new Failure(Failure.Type.NOT_AUTHORIZED, e.getCause().getMessage()));
      }
      else {
        log.log(Level.WARNING, "Exception during response evaluating", e);
        throw new RuntimeException(e);
      }
    }
  }

  public void invoke(Auth auth) {
    sasl = this.auth.get(auth.mechanism());
    if (!sasl.isComplete()) {
      final byte[] response;
      try {
        response = sasl.evaluateResponse(new byte[0]);
        answer(new Challenge(response));
      }
      catch (SaslException e) {
        log.log(Level.WARNING, "Exception during initial challenge generation", e);
        throw new RuntimeException(e);
      }
    }
    else answer(new Success());
  }
}
