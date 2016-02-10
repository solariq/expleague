package com.expleague.xmpp.control.sasl.plain;

/**
 * User: solar
 * Date: 16.12.15
 * Time: 22:51
 */
public interface SaslStateContext {

  /**
   * Set the state to use for the next incoming message.
   *
   * @param newState the new state
   */
  void setNegotiationState(SaslState newState);

  /**
   * Indicate that negotiation is complete.  To re-initiate negotiation, call {@link #setNegotiationState(SaslState)}.
   */
  void negotiationComplete();
}