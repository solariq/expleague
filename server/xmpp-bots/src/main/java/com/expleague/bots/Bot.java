package com.expleague.bots;

import com.expleague.bots.utils.ExpectedMessage;
import com.expleague.xmpp.AnyHolder;
import com.expleague.xmpp.Item;
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

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * User: solar
 * Date: 18.10.15
 * Time: 16:17
 */
public class Bot {
  public static final String TBTS_XMLNS = "http://expleague.com/scheme";
  private static final long DEFAULT_TIMEOUT_IN_NANOS = 60L * 1000L * 1000L * 1000L;
  private static final long LOGIN_SLEEP_TIMEOUT_IN_MILLIS = 5L * 1000L;

  private final String passwd;
  private final String resource;
  private final String email;
  private final BareJID jid;

  protected final Jaxmpp jaxmpp = new Jaxmpp(new J2SESessionObject());
  private final BlockingQueue<Message> messagesQueue = new LinkedBlockingQueue<>();

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

    try {
      jaxmpp.login();
    } catch (JaxmppException je) {
      try {
        Thread.sleep(LOGIN_SLEEP_TIMEOUT_IN_MILLIS);
      } catch (InterruptedException e) {
        e.printStackTrace();
      } finally {
        jaxmpp.login();
      }
    }

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
        onMessage(stanza);
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

    jaxmpp.login();
    latch.state(2, 1);
    System.out.println("Logged in");
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

  public void online() throws JaxmppException {
    final Presence stanza = Presence.create();
    jaxmpp.send(stanza);
    System.out.println("Online presence sent");
  }

  public void offline() throws JaxmppException {
    final Presence presence = Presence.create();
    presence.setShow(Presence.Show.xa);
    jaxmpp.send(presence);
    System.out.println("Sent offline presence");
  }

  public void sendCancel(BareJID roomJID) throws JaxmppException {
    final Element cancelElem = ElementFactory.create("cancel");
    cancelElem.setXMLNS(TBTS_XMLNS);
    sendToGroupChat(cancelElem, JID.jidInstance(roomJID));
  }

  public void sendTextMessageToRoom(BareJID roomJID, com.expleague.xmpp.stanza.Message.Body body) throws JaxmppException {
    final Message message = Message.create();
    message.setTo(JID.jidInstance(roomJID));
    message.setType(StanzaType.groupchat);
    message.setBody(body.value());
    jaxmpp.send(message);
  }

  protected void sendToGroupChat(Element element, JID to) throws JaxmppException {
    final Message message = Message.create();
    message.addChild(element);
    message.setType(StanzaType.groupchat);
    message.setTo(to);
    jaxmpp.send(message);
  }

  public ExpectedMessage[] tryReceiveMessages(StateLatch stateLatch, ExpectedMessage... messages) {
    return tryReceiveMessages(stateLatch, DEFAULT_TIMEOUT_IN_NANOS, messages);
  }

  public ExpectedMessage[] tryReceiveMessages(StateLatch stateLatch, long timeoutInNanos, ExpectedMessage... expectedMessages) {
    final int initState = stateLatch.state();
    final int finalState = initState << expectedMessages.length;
    final Thread messagesConsumer = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        final Message message;
        try {
          message = messagesQueue.take();
          final AnyHolder anyHolder = Item.create(message.getAsString());
          for (ExpectedMessage expectedMessage : expectedMessages) {
            if (!expectedMessage.received() && expectedMessage.tryReceive(anyHolder)) {
              stateLatch.advance();
              if (stateLatch.state() == initState) {
                return;
              }
              break;
            }
          }
        } catch (InterruptedException ignored) {
          Thread.currentThread().interrupt();
          return;
        } catch (JaxmppException ignored) {
        }
      }
    });

    messagesConsumer.start();
    stateLatch.state(finalState, initState, timeoutInNanos);
    messagesConsumer.interrupt();
    if (stateLatch.state() != initState) {
      stateLatch.state(initState);
    }
    return Arrays.stream(expectedMessages).filter(em -> !em.received()).toArray(ExpectedMessage[]::new);
  }

  private synchronized void onMessage(Message message) throws JaxmppException {
    { //sending receipts
      final String receivedXMLNS = "urn:xmpp:receipts";
      final Element request = message.getFirstChild("request");
      if (request != null && receivedXMLNS.equals(request.getXMLNS())) {
        final Message receivedMessage = Message.create();
        receivedMessage.setType(StanzaType.normal);

        final Element received = receivedMessage.addChild(ElementFactory.create("received"));
        received.setAttribute("id", message.getId());
        received.setXMLNS(receivedXMLNS);
        jaxmpp.send(receivedMessage);
      }
    }
    messagesQueue.offer(message);
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
