package com.tbts.server.xmpp.phase;

import akka.actor.ActorRef;
import com.tbts.server.Roster;
import com.tbts.server.xmpp.XMPPClientConnection;
import com.tbts.xmpp.Features;
import com.tbts.xmpp.control.register.Query;
import com.tbts.xmpp.control.register.Register;
import com.tbts.xmpp.control.sasl.*;
import com.tbts.xmpp.stanza.IqH;
import com.tbts.xmpp.stanza.data.Err;

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
@SuppressWarnings("unused")
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
        auth,
        new Register()
    ));
  }

  public void invoke(IqH<Query> request) {
    final Query query = request.get();
    if (query != null) {
      if (request.type() == IqH.IqType.GET && query.isEmpty()) {
        answer(IqH.answer(request, Roster.instance().required()));
      }
      else if (request.type() == IqH.IqType.SET && !query.isEmpty()) {
        try {
          Roster.instance().register(query);
          answer(IqH.answer(request));
        }
        catch (Exception e) {
          answer(IqH.answer(request, new Err(Err.Cause.INSTERNAL_SERVER_ERROR, Err.ErrType.AUTH, e.getMessage())));
        }
      }
    }
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
        stop();
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
