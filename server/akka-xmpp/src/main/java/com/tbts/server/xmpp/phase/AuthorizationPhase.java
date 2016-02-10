package com.tbts.server.xmpp.phase;

import akka.actor.ActorRef;
import com.spbsu.commons.func.Action;
import com.tbts.server.Roster;
import com.tbts.server.xmpp.XMPPClientConnection;
import com.tbts.xmpp.Features;
import com.tbts.xmpp.control.register.Query;
import com.tbts.xmpp.control.register.Register;
import com.tbts.xmpp.control.sasl.*;
import com.tbts.xmpp.stanza.Iq;
import com.tbts.xmpp.stanza.Iq.IqType;
import com.tbts.xmpp.stanza.data.Err;

import javax.security.sasl.AuthenticationException;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.sql.SQLIntegrityConstraintViolationException;
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
  private final Mechanisms auth;
  private final Action<String> authorizedCallback;
  private SaslServer sasl;

  public AuthorizationPhase(ActorRef connection, Action<String> authorizedCallback) {
    super(connection);
    this.authorizedCallback = authorizedCallback;
    auth = new Mechanisms();
    auth.fillKnownMechanisms();
  }

  public void invoke(Iq<Query> request) {
    final Query query = request.get();
    if (query != null) {
      if (request.type() == IqType.GET && query.isEmpty()) {
        answer(Iq.answer(request, Roster.instance().required()));
      }
      else if (request.type() == IqType.SET && !query.isEmpty()) {
        try {
          Roster.instance().register(query);
          answer(Iq.answer(request));
        }
        catch (SQLIntegrityConstraintViolationException integrity) {
          answer(Iq.answer(request, new Err(Err.Cause.CONFLICT, Err.ErrType.AUTH, integrity.getMessage())));
        }
        catch (Exception e) {
          log.log(Level.FINEST, "Exception during user registration", e);
          answer(Iq.answer(request, new Err(Err.Cause.INTERNAL_SERVER_ERROR, Err.ErrType.AUTH, e.getMessage())));
        }
      }
    }
  }

  public void invoke(Response response) {
    if (sasl == null)
      throw new IllegalStateException();
    processAuth(response.data());
  }

  public void invoke(Auth auth) {
    sasl = this.auth.get(auth.mechanism());
    processAuth(auth.challenge());
  }

  public void processAuth(byte[] data) {
    if (!sasl.isComplete()) {
      try {
        final byte[] challenge = sasl.evaluateResponse(data != null ? data : new byte[0]);
        if (challenge != null && challenge.length > 0) {
          answer(new Challenge(challenge));
        }
        if (sasl.isComplete())
          success();
      }
      catch (AuthenticationException e) {
        answer(new Failure(Failure.Type.NOT_AUTHORIZED, e.getMessage()));
      }
      catch (SaslException e) {
        if (e.getCause() instanceof AuthenticationException) {
          answer(new Failure(Failure.Type.NOT_AUTHORIZED, e.getCause().getMessage()));
        }
        else {
          log.log(Level.WARNING, "Exception during authorization", e);
          throw new RuntimeException(e);
        }
      }
    }
    else {
      success();
    }
  }

  public void success() {
    authorizedCallback.invoke(sasl.getAuthorizationID());
    last(new Success(), XMPPClientConnection.ConnectionState.CONNECTED);
  }

  @Override
  public void open() {
    answer(new Features(
        auth,
        new Register()
    ));
  }
}
