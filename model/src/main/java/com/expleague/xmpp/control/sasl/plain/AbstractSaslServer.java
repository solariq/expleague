package com.expleague.xmpp.control.sasl.plain;

/**
 * User: solar
 * Date: 16.12.15
 * Time: 22:52
 */

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

/**
 * A base class for SASL client implementations.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractSaslServer extends AbstractSaslParticipant implements SaslServer {

  /**
   * Construct a new instance.
   *
   * @param mechanismName the name of the defined mechanism
   * @param protocol the protocol
   * @param serverName the server name
   * @param callbackHandler the callback handler
   */
  protected AbstractSaslServer(final String mechanismName, final String protocol, final String serverName, final CallbackHandler callbackHandler) {
    super(mechanismName, protocol, serverName, callbackHandler);
  }

  /**
   * Evaluate an authentication response received from the client.
   *
   * @param response the authentication response
   * @return the next challenge to send to the client
   * @throws SaslException if there is an error processing the client message
   */
  public byte[] evaluateResponse(final byte[] response) throws SaslException {
    return evaluateMessage(response);
  }
}