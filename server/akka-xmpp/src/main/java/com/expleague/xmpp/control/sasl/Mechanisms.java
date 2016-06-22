package com.expleague.xmpp.control.sasl;

import com.expleague.server.XMPPDevice;
import com.expleague.server.ExpLeagueServer;
import com.expleague.xmpp.control.XMPPFeature;
import com.expleague.xmpp.control.sasl.plain.PlainServer;
import com.spbsu.commons.func.Functions;

import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.sasl.*;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.*;
import java.util.stream.Stream;

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
//    while (factories.hasMoreElements()) {
//      final SaslServerFactory saslServerFactory = factories.nextElement();
//      for (final String mech : saslServerFactory.getMechanismNames(Collections.emptyMap())) {
////        System.out.println(mech);
//        if ("GSSAPI".equals(mech)) // skip kerberos from MS
//          continue;
//        mechanisms.add(adapter.unmarshal(mech));
//      }
//    }
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
          final Optional<NameCallback> nameO = Stream.of(callbacks).flatMap(Functions.instancesOf(NameCallback.class)).findAny();
          final Optional<PasswordCallback> passwdO = Stream.of(callbacks).flatMap(Functions.instancesOf(PasswordCallback.class)).findAny();
          final Optional<AuthorizeCallback> authO = Stream.of(callbacks).flatMap(Functions.instancesOf(AuthorizeCallback.class)).findAny();
          if (passwdO.isPresent() && nameO.isPresent()) {
            final PasswordCallback passwd = passwdO.get();
            final XMPPDevice user = ExpLeagueServer.roster().device(nameO.get().getDefaultName());
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
          return new PlainServer("xmpp", ExpLeagueServer.config().domain(), callbackHandler);
        else return Sasl.createSaslServer(mechanism, "xmpp", ExpLeagueServer.config().domain(), Collections.emptyMap(), callbackHandler);
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
