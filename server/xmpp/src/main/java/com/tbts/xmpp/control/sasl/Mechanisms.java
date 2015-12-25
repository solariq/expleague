package com.tbts.xmpp.control.sasl;

import com.tbts.server.JabberUser;
import com.tbts.server.TBTSServer;
import com.tbts.xmpp.control.XMPPFeature;
import com.tbts.xmpp.control.sasl.plain.PlainServer;

import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.sasl.*;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.*;

/**
 * User: solar
 * Date: 10.12.15
 * Time: 16:14
 */
@XmlRootElement
public class Mechanisms extends XMPPFeature {
  @XmlElement(name = "mechanism", namespace = "urn:ietf:params:xml:ns:xmpp-sasl")
  @XmlJavaTypeAdapter(AuthMechanismXmlAdapter.class)
  private final List<SaslServer> mechanisms = new ArrayList<>();

  public void fillKnownMechanisms() {
    final Enumeration<SaslServerFactory> factories = Sasl.getSaslServerFactories();
    final AuthMechanismXmlAdapter adapter = new AuthMechanismXmlAdapter();
    while (factories.hasMoreElements()) {
      final SaslServerFactory saslServerFactory = factories.nextElement();
      for (final String mech : saslServerFactory.getMechanismNames(Collections.emptyMap())) {
//        System.out.println(mech);
        if ("GSSAPI".equals(mech)) // skip kerberos from MS
          continue;
        mechanisms.add(adapter.unmarshal(mech));
      }
    }
    mechanisms.add(adapter.unmarshal("PLAIN"));
  }

  public SaslServer get(String mechanism) {
    return mechanisms.stream().filter(sasl -> mechanism.equals(sasl.getMechanismName())).findAny().get();
  }

  public static class AuthMechanismXmlAdapter extends XmlAdapter<String, SaslServer> {
    @Override
    public SaslServer unmarshal(String mechanism) {
      try {
        final CallbackHandler callbackHandler = callbacks -> {
          final Optional<NameCallback> nameO = Arrays.asList(callbacks).stream().filter(callback -> callback instanceof NameCallback).map(a -> (NameCallback) a).findAny();
          final Optional<PasswordCallback> passwdO = Arrays.asList(callbacks).stream().filter(callback -> callback instanceof PasswordCallback).map(a -> (PasswordCallback) a).findAny();
          final Optional<AuthorizeCallback> authO = Arrays.asList(callbacks).stream().filter(callback -> callback instanceof AuthorizeCallback).map(a -> (AuthorizeCallback) a).findAny();
          if (passwdO.isPresent() && nameO.isPresent()) {
            final PasswordCallback passwd = passwdO.get();
            final JabberUser user = TBTSServer.roster().byName(nameO.get().getDefaultName());
            if (user != null)
              passwd.setPassword(user.passwd().toCharArray());
            else
              throw new AuthenticationException("No such user");
          }
          if (authO.isPresent()) {
            final AuthorizeCallback auth = authO.get();
            if (auth.getAuthenticationID().equals(auth.getAuthorizationID())) {
              auth.setAuthorized(true);
            }
          }
        };
        if ("PLAIN".equals(mechanism))
          return new PlainServer("xmpp", TBTSServer.config().domain(), callbackHandler);
        else return Sasl.createSaslServer(mechanism, "xmpp", TBTSServer.config().domain(), Collections.emptyMap(), callbackHandler);
      } catch (SaslException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public String marshal(SaslServer v) {
      return v.getMechanismName();
    }
  }
}
