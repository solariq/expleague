package com.expleague.xmpp.control.sasl.plain;

/**
 * User: solar
 * Date: 16.12.15
 * Time: 22:53
 */

import javax.security.sasl.SaslException;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface SaslWrapper {
  byte[] wrap(byte[] outgoing, final int offset, final int len) throws SaslException;

  byte[] unwrap(byte[] incoming, final int offset, final int len) throws SaslException;
}