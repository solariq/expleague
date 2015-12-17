package com.tbts.xmpp.control.sasl.plain;

import javax.security.sasl.SaslException;

/**
 * User: solar
 * Date: 16.12.15
 * Time: 22:50
 */
class ExceptionSaslState implements SaslState {
  private final String text;

  ExceptionSaslState(final String text) {
    this.text = text;
  }

  public byte[] evaluateMessage(final SaslStateContext context, final byte[] message) throws SaslException {
    throw new SaslException(text);
  }
}