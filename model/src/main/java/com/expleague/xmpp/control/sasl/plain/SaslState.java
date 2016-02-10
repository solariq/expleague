package com.expleague.xmpp.control.sasl.plain;

import javax.security.sasl.SaslException;

/**
 * User: solar
 * Date: 16.12.15
 * Time: 22:50
 */
public interface SaslState {

  /**
   * Evaluate a SASL challenge or response message.
   *
   * @param context the state context
   * @param message the message to evaluate
   * @return the reply message
   * @throws SaslException if negotiation has failed
   */
  byte[] evaluateMessage(SaslStateContext context, byte[] message) throws SaslException;

  /**
   * The SASL negotiation failure state.
   */
  SaslState FAILED = new ExceptionSaslState("SASL negotiation failed");

  /**
   * The SASL negotiation completed state.
   */
  SaslState COMPLETE = new ExceptionSaslState("SASL negotiation already complete");
}