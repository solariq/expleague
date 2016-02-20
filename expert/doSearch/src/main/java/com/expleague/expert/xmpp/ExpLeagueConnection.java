package com.expleague.expert.xmpp;

import com.expleague.expert.profile.ProfileManager;
import com.expleague.expert.profile.UserProfile;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.stanza.Stanza;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.spbsu.commons.random.FastRandom;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.criteria.Criteria;
import tigase.jaxmpp.core.client.criteria.ElementCriteria;
import tigase.jaxmpp.core.client.criteria.Or;
import tigase.jaxmpp.core.client.eventbus.Event;
import tigase.jaxmpp.core.client.eventbus.EventBus;
import tigase.jaxmpp.core.client.eventbus.EventHandler;
import tigase.jaxmpp.core.client.eventbus.EventListener;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.AbstractStanzaModule;
import tigase.jaxmpp.core.client.xmpp.modules.SessionEstablishmentModule;
import tigase.jaxmpp.core.client.xmpp.modules.auth.AuthModule;
import tigase.jaxmpp.core.client.xmpp.modules.auth.SaslModule;
import tigase.jaxmpp.core.client.xmpp.modules.registration.InBandRegistrationModule;
import tigase.jaxmpp.core.client.xmpp.modules.roster.RosterModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.core.client.xmpp.stanzas.StreamPacket;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;
import tigase.jaxmpp.j2se.xml.J2seElement;
import tigase.xml.DefaultElementFactory;
import tigase.xml.DomBuilderHandler;
import tigase.xml.SimpleParser;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.GeneralSecurityException;
import java.util.Timer;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 09/02/16.
 */
public class ExpLeagueConnection extends WeakListenerHolderImpl<ExpLeagueConnection.Status> implements EventListener {
  private static final Logger log = Logger.getLogger(ExpLeagueConnection.class.getName());
  private static ExpLeagueConnection instance;
  private static Timer ping;
  private ExpLeagueMember expert;

  public static synchronized ExpLeagueConnection instance() {
    if (instance == null) {
//      final SSLContext sslctx;
//      try {
//        sslctx = SSLContext.getInstance("TLS");
//        sslctx.init(null, new TrustManager[]{
//            new TLSUtils.AcceptAllTrustManager()
//        }, new SecureRandom());
//      }
//      catch (KeyManagementException | NoSuchAlgorithmException e) {
//        log.log(Level.SEVERE, "Unable to create SSL context needed to connect to ExpLeague servers");
//        throw new RuntimeException(e);
//      }
      instance = new ExpLeagueConnection();
      ping = new Timer("XMPP ping", true);
//      ping.schedule(new TimerTask() {
//        @Override
//        public void run() {
//          final Jaxmpp jaxmpp = instance.jaxmpp;
//          if (jaxmpp != null) {
//            try {
//              jaxmpp.getConnector().keepalive();
//            } catch (JaxmppException e) {
//              e.printStackTrace();
//            }
//          }
//        }
//      }, 0, TimeUnit.SECONDS.toMillis(30));
//      SASLAuthentication.registerSASLMechanism(new SASLDigestMD5Mechanism());
    }
    return instance;
  }

  private Jaxmpp jaxmpp;

//  private SSLContext sslctx;

  private FastRandom fastRandom = new FastRandom();

  public void start(UserProfile profile) {
    start(profile, true);
  }

  private void start(UserProfile profile, boolean tryToRegister) {
    try {
      if (jaxmpp != null && jaxmpp.isConnected())
        jaxmpp.disconnect();
      jaxmpp = new Jaxmpp();
      final XmppModulesManager modulesManager = jaxmpp.getModulesManager();
      this.expert = profile.expert();
      modulesManager.register(new AbstractStanzaModule<tigase.jaxmpp.core.client.xmpp.stanzas.Stanza>() {
        @Override
        public Criteria getCriteria() {
          return new Or(new ElementCriteria("message", null, null), new ElementCriteria("presence", null, null));
        }

        @Override
        public String[] getFeatures() {
          return null;
        }

        @Override
        public void process(tigase.jaxmpp.core.client.xmpp.stanzas.Stanza stanza) throws JaxmppException {
        }
      });
      modulesManager.register(new RosterModule());

      final UserProperties properties = jaxmpp.getProperties();
      properties.setUserProperty(SessionObject.DOMAIN_NAME, profile.get(UserProfile.Key.EXP_LEAGUE_DOMAIN));
      properties.setUserProperty(SocketConnector.HOSTNAME_VERIFIER_DISABLED_KEY, true);
      properties.setUserProperty(SessionObject.USER_BARE_JID, BareJID.bareJIDInstance(profile.get(UserProfile.Key.EXP_LEAGUE_ID)));
      properties.setUserProperty(SessionObject.PASSWORD, profile.get(UserProfile.Key.EXP_LEAGUE_PASSWORD));
      properties.setUserProperty(SessionObject.RESOURCE, "expert");

//      jaxmpp.getContext().getSessionObject().setProperty(Connector.EXTERNAL_KEEPALIVE_KEY, true);
      final EventBus eventBus = jaxmpp.getEventBus();
      eventBus.addListener(JaxmppCore.DisconnectedHandler.DisconnectedEvent.class, this);
      eventBus.addListener(SessionEstablishmentModule.SessionEstablishmentSuccessHandler.SessionEstablishmentSuccessEvent.class, this);
      eventBus.addListener(Connector.StanzaReceivedHandler.StanzaReceivedEvent.class, this);
      if (tryToRegister) {
        eventBus.addHandler(AuthModule.AuthFailedHandler.AuthFailedEvent.class, (sessionObject, saslError) -> {
          if (saslError == SaslModule.SaslError.not_authorized) {
            jaxmpp.disconnect();
            register(profile);
            start(profile, false);
          }
        });
      }
      try {
        jaxmpp.login();
      }
      catch (Exception ignore) {
      }
    }
    catch (JaxmppException e) {
      log.log(Level.WARNING, "Unable to connect to " + profile.get(UserProfile.Key.EXP_LEAGUE_DOMAIN) + " by " + profile.get(UserProfile.Key.EXP_LEAGUE_ID));
    }
  }
  public void register(UserProfile profile) {
    final Jaxmpp jaxmpp = new Jaxmpp();
    final UserProperties properties = jaxmpp.getProperties();
    final String passwd = fastRandom.nextBase64String(15);
    final String jid = profile.get(UserProfile.Key.EXP_LEAGUE_ID);

    profile.set(UserProfile.Key.EXP_LEAGUE_PASSWORD, passwd);

    properties.setUserProperty(SessionObject.DOMAIN_NAME, profile.get(UserProfile.Key.EXP_LEAGUE_DOMAIN));
    properties.setUserProperty(SocketConnector.HOSTNAME_VERIFIER_DISABLED_KEY, true);
    properties.setUserProperty(SessionObject.RESOURCE, "expert");
    jaxmpp.getModulesManager().register(new InBandRegistrationModule());
    jaxmpp.getSessionObject().setProperty(InBandRegistrationModule.IN_BAND_REGISTRATION_MODE_KEY, Boolean.TRUE);
    CountDownLatch latch = new CountDownLatch(1);
    jaxmpp.getEventBus().addHandler(
        InBandRegistrationModule.ReceivedRequestedFieldsHandler.ReceivedRequestedFieldsEvent.class,
        (sessionObject, responseStanza) -> {
          try {
            final IQ iq = IQ.create();
            iq.setType(StanzaType.set);
            iq.setTo(JID.jidInstance((String)profile.get(UserProfile.Key.EXP_LEAGUE_DOMAIN)));
            Element q = ElementFactory.create("query", null, "jabber:iq:register");
            iq.addChild(q);
            q.addChild(ElementFactory.create("username", jid, null));
            q.addChild(ElementFactory.create("password", passwd, null));
            q.addChild(ElementFactory.create("misc", profile.get(UserProfile.Key.AVATAR_URL), null));
            q.addChild(ElementFactory.create("name", profile.get(UserProfile.Key.NAME), null));
            q.addChild(ElementFactory.create("city", profile.get(UserProfile.Key.CITY), null));
            q.addChild(ElementFactory.create("state", profile.get(UserProfile.Key.COUNTRY), null));
            jaxmpp.send(iq, new AsyncCallback() {
              @Override
              public void onError(tigase.jaxmpp.core.client.xmpp.stanzas.Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {
                log.log(Level.SEVERE, "Unable to register expert", errorCondition);
                finish();
              }

              @Override
              public void onSuccess(tigase.jaxmpp.core.client.xmpp.stanzas.Stanza stanza) throws JaxmppException {
                finish();
              }

              @Override
              public void onTimeout() throws JaxmppException {
                log.severe("Timeout during registering expert");
                finish();
              }

              private void finish() throws JaxmppException {
                jaxmpp.getConnector().stop();
                latch.countDown();
              }
            });
          } catch (JaxmppException e) {
            throw new RuntimeException(e);
          }
        });
    try {
      jaxmpp.login();
      latch.await();
      jaxmpp.disconnect();
    }
    catch (JaxmppException | InterruptedException e) {
      log.log(Level.SEVERE, "Exception during expert registration", e);
    }

  }

  public void start() {
    final UserProfile active = ProfileManager.instance().active();
    if (active != null)
      start(active, true);
  }

  public void stop() {
    try {
      jaxmpp.disconnect();
    }
    catch (JaxmppException e) {
      log.log(Level.WARNING, "Error during disconnect", e);
      invoke(Status.DISCONNECTED);
    }
  }

  private Status status = Status.DISCONNECTED;
  @Override
  protected void invoke(Status e) {
    status = e;
    super.invoke(e);
  }

  public Status status() {
    return status;
  }

  public void send(Stanza stanza) {
    SimpleParser parser = new SimpleParser();
    final String xmlString = stanza.xmlString();
    final DomBuilderHandler handler = new DomBuilderHandler(new DefaultElementFactory());
    parser.parse(handler, xmlString.toCharArray(), 0, xmlString.length());
    try {
      log.info("<" + stanza.xmlString());
      jaxmpp.send(tigase.jaxmpp.core.client.xmpp.stanzas.Stanza.create(new J2seElement(handler.getParsedElements().peek())));
    }
    catch (JaxmppException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void onEvent(Event<? extends EventHandler> event) {
    log.info(event.toString());
    if (event instanceof SessionEstablishmentModule.SessionEstablishmentSuccessHandler.SessionEstablishmentSuccessEvent) {
      log.info("Authenticated as " + expert.jid());
      status = Status.CONNECTED;
      invoke(Status.CONNECTED);
      try {
        jaxmpp.send(tigase.jaxmpp.core.client.xmpp.stanzas.Stanza.createPresence());
      }
      catch (JaxmppException e) {
        // ignore
      }
    }
    else if (event instanceof JaxmppCore.DisconnectedHandler.DisconnectedEvent) {
      status = Status.DISCONNECTED;
      invoke(Status.DISCONNECTED);
    }
    else if (event instanceof Connector.StanzaReceivedHandler.StanzaReceivedEvent) {
      final StreamPacket streamPacket = ((Connector.StanzaReceivedHandler.StanzaReceivedEvent) event).getStanza();
      try {
        log.info(">" + streamPacket.getAsString());
        final Item item = Item.create(streamPacket.getAsString());
        if (item instanceof Stanza)
          expert.processPacket((Stanza) item);
      }
      catch (XMLException e) {
        log.log(Level.SEVERE, "Unable to parse incoming message", e);
        throw new RuntimeException(e);
      }
    }
//    System.out.println(event);
  }

  public void disconnect() throws JaxmppException{
    if (jaxmpp != null && jaxmpp.isConnected())
      jaxmpp.disconnect();
  }

  public enum Status {
    CONNECTED,
    DISCONNECTED,
  }

  static {
    // Create a trust manager that does not validate certificate chains
    TrustManager[] trustAllCerts = new TrustManager[] {
        new X509TrustManager() {
          public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return null;
          }
          public void checkClientTrusted(
              java.security.cert.X509Certificate[] certs, String authType) {
          }
          public void checkServerTrusted(
              java.security.cert.X509Certificate[] certs, String authType) {
          }
        }
    };

// Install the all-trusting trust manager
    try {
      SSLContext sc = SSLContext.getInstance("SSL");
      sc.init(null, trustAllCerts, new java.security.SecureRandom());
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    } catch (GeneralSecurityException e) {
    }
  }
}
