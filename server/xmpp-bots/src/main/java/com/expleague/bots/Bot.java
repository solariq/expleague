package com.expleague.bots;

import com.spbsu.commons.util.sync.StateLatch;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.criteria.Criteria;
import tigase.jaxmpp.core.client.criteria.ElementCriteria;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.AbstractStanzaModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.modules.registration.InBandRegistrationModule;
import tigase.jaxmpp.core.client.xmpp.modules.roster.RosterModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.*;
import tigase.jaxmpp.j2se.J2SEPresenceStore;
import tigase.jaxmpp.j2se.J2SESessionObject;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;

import java.util.ArrayList;
import java.util.List;

/**
 * User: solar
 * Date: 18.10.15
 * Time: 16:17
 */
public class Bot {
  public static final String TBTS_XMLNS = "http://expleague.com/scheme";
  private static final long DEFAULT_TIMEOUT_IN_NANOS = 60L * 1000L * 1000L * 1000L;

  private final String passwd;
  private final String resource;
  private final String email;
  private final BareJID jid;

  protected final Jaxmpp jaxmpp = new Jaxmpp(new J2SESessionObject());

  public Bot(final BareJID jid, final String passwd, String resource) {
    this(jid, passwd, resource, null);
  }

  public Bot(final BareJID jid, final String passwd, String resource, String email) {
    this.jid = jid;
    this.passwd = passwd;
    this.resource = resource;
    this.email = email;

    jaxmpp.getProperties().setUserProperty(SessionObject.DOMAIN_NAME, jid.getDomain());
    jaxmpp.getProperties().setUserProperty(SocketConnector.HOSTNAME_VERIFIER_DISABLED_KEY, true);
//    jaxmpp.getSessionObject().setProperty(SocketConnector.TLS_DISABLED_KEY, true);
    jaxmpp.getModulesManager().register(new MucModule());
    PresenceModule.setPresenceStore(jaxmpp.getSessionObject(), new J2SEPresenceStore());
    jaxmpp.getModulesManager().register(new PresenceModule());
    jaxmpp.getModulesManager().register(new RosterModule());
  }

  public void start() throws JaxmppException {
    jaxmpp.getModulesManager().register(new InBandRegistrationModule());
    jaxmpp.getSessionObject().setProperty(InBandRegistrationModule.IN_BAND_REGISTRATION_MODE_KEY, Boolean.TRUE);
    final StateLatch latch = new StateLatch();
    latch.state(1);
    jaxmpp.getEventBus().addHandler(
        InBandRegistrationModule.ReceivedRequestedFieldsHandler.ReceivedRequestedFieldsEvent.class,
        new InBandRegistrationModule.ReceivedRequestedFieldsHandler() {
          public void onReceivedRequestedFields(SessionObject sessionObject, IQ responseStanza) {
            try {
              final InBandRegistrationModule module = jaxmpp.getModule(InBandRegistrationModule.class);
              module.register(jid.toString(), passwd, email, new PrinterAsyncCallback("register", latch) {
                @Override
                protected void transactionComplete() {
                  super.transactionComplete();
                  try {
                    jaxmpp.getConnector().stop();
                  } catch (JaxmppException e) {
                    throw new RuntimeException(e);
                  }
                }
              });
            } catch (JaxmppException e) {
              throw new RuntimeException(e);
            }
          }
        });
    jaxmpp.login();
    latch.state(2, 1);
    System.out.println("Registration phase passed");
    jaxmpp.getSessionObject().setProperty(InBandRegistrationModule.IN_BAND_REGISTRATION_MODE_KEY, Boolean.FALSE);
    jaxmpp.getProperties().setUserProperty(SessionObject.USER_BARE_JID, jid);
    jaxmpp.getProperties().setUserProperty(SessionObject.PASSWORD, passwd);
    jaxmpp.getProperties().setUserProperty(SessionObject.RESOURCE, resource);
//    jaxmpp.getModulesManager().register(new )
    jaxmpp.getEventBus().addHandler(JaxmppCore.ConnectedHandler.ConnectedEvent.class, sessionObject -> latch.advance());
    jaxmpp.getModulesManager().register(new AbstractStanzaModule<Message>() {
      @Override
      public Criteria getCriteria() {
        return new ElementCriteria("message", new String[0], new String[0]);
      }

      @Override
      public String[] getFeatures() {
        return new String[0];
      }

      @Override
      public void process(Message stanza) throws JaxmppException {
        onMessage(stanza.getAsString());
      }
    });

    jaxmpp.getEventBus().addHandler(MucModule.MucMessageReceivedHandler.MucMessageReceivedEvent.class, (sessionObject, message, room, s, date) -> {
      try {
        System.out.println("Group: " + message.getAsString());
        latch.advance();
      } catch (XMLException e) {
        throw new RuntimeException(e);
      }
    });

    jaxmpp.getEventBus().addHandler(PresenceModule.ContactAvailableHandler.ContactAvailableEvent.class,
        (sessionObject, presence, jid, show, s, integer) -> System.out.println(jid + " available with message " + presence.getStatus()));

    jaxmpp.getEventBus().addHandler(Connector.StanzaReceivedHandler.StanzaReceivedEvent.class, (sessionObject, stanza) -> {
      try {
        System.out.println("Msg: " + stanza.getAsString());
      } catch (XMLException e) {
        throw new RuntimeException(e);
      }
    });

    jaxmpp.login();
    latch.state(2, 1);
    System.out.println("Logged in");

  }

  protected void onMessage(String asString) {
  }

  public void stop() throws JaxmppException {
    final IQ iq = IQ.create();
    iq.setType(StanzaType.set);
    iq.setTo(JID.jidInstance((String) jaxmpp.getSessionObject().getProperty("domainName")));
    final Element q = ElementFactory.create("query", null, "jabber:iq:register");
    iq.addChild(q);
    q.addChild(ElementFactory.create("remove"));
    final StateLatch latch = new StateLatch();

    jaxmpp.getEventBus().addHandler(JaxmppCore.DisconnectedHandler.DisconnectedEvent.class, sessionObject -> latch.advance());
    jaxmpp.send(iq);
    jaxmpp.disconnect();
    latch.state(2, 1);
  }

  public BareJID jid() {
    return jid;
  }

  public void online() {
    try {
      final Presence stanza = Presence.create();
      jaxmpp.send(stanza);
      System.out.println("Online presence sent");
    } catch (JaxmppException e) {
      throw new RuntimeException(e);
    }
  }

  public Message receiveMessage() {
    return receiveMessage(DEFAULT_TIMEOUT_IN_NANOS);
  }

  public Message receiveMessage(long timeoutInNanos) {
    List<Message> messages = receiveMessages(1, timeoutInNanos);
    return messages.get(0);
  }

  public List<Message> receiveMessages(int messagesNum) {
    return receiveMessages(messagesNum, DEFAULT_TIMEOUT_IN_NANOS);
  }

  public List<Message> receiveMessages(int messagesNum, long timeoutInNanos) {
    final StateLatch latch = new StateLatch();
    final List<Message> messages = new ArrayList<>();
    final Connector.StanzaReceivedHandler handler = (sessionObject, stanza) -> {
      if (stanza instanceof Message) {
        messages.add((Message) stanza);
        latch.advance();
      }
    };
    jaxmpp.getEventBus().addHandler(Connector.StanzaReceivedHandler.StanzaReceivedEvent.class, handler);
    latch.state(1 << messagesNum, 1, timeoutInNanos);
    jaxmpp.getEventBus().remove(Connector.StanzaReceivedHandler.StanzaReceivedEvent.class, handler);

    if (messages.size() < messagesNum) {
      throw new RuntimeException("timeout");
    }
    return messages;
  }

  public void sendToGroupChat(String chatMessage, BareJID roomJID) {
    try {
      final Message message = Message.create();
      message.setTo(JID.jidInstance(roomJID));
      message.setType(StanzaType.groupchat);
      message.setBody(chatMessage);
      jaxmpp.send(message);
    } catch (JaxmppException e) {
      throw new RuntimeException(e);
    }
  }

  protected void onClose(final Runnable runnable) {
    jaxmpp.getEventBus().addHandler(Connector.DisconnectedHandler.DisconnectedEvent.class, sessionObject -> runnable.run());
  }

  public static class PrinterAsyncCallback implements AsyncCallback {
    private final String name;

    private StateLatch lock;

    public PrinterAsyncCallback(String name, StateLatch lock) {
      this.name = name;
      this.lock = lock;
    }

    public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
      System.out.println("Error [" + name + "]: " + error + " [" + responseStanza.getAsString() + "]");
      transactionComplete();
    }

    public void onSuccess(Stanza responseStanza) throws JaxmppException {
      System.out.println("Response  [" + name + "]: " + responseStanza.getAsString());
      transactionComplete();
    }

    public void onTimeout() throws JaxmppException {
      System.out.println("Timeout " + name);
      transactionComplete();
    }

    protected void transactionComplete() {
      lock.advance();
    }

  }
}
