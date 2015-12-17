package com.tbts.xmpp.control.sasl.plain;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;
import java.util.Map;

/**
 * User: solar
 * Date: 16.12.15
 * Time: 22:44
 */
public class PlainServerFactory extends AbstractSaslFactory implements SaslServerFactory {

    /**
     * The PLAIN mechanism name
     */
    public static final String PLAIN = "PLAIN";

    /**
     * Default constructor.
     */
    public PlainServerFactory() {
      this(PLAIN);
    }

    /**
     * Construct a new instance.
     *
     * @param name the mechanism name
     */
    protected PlainServerFactory(final String name) {
      super(name);
    }

  public SaslServer createSaslServer(String mechanism, String protocol, String serverName, Map<String, ?> props, CallbackHandler cbh) throws SaslException {
    // Unless we are sure plain is required don't return a SaslServer
    if (PLAIN.equals(mechanism) == false || matches(props) == false) {
      return null;
    }

    return new PlainServer(protocol, serverName, cbh);
  }

  @Override
  protected boolean isAnonymous() {
    return false;
  }

}
