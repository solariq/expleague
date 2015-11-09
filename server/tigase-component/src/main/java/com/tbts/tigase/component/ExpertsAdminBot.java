package com.tbts.tigase.component;

import com.spbsu.commons.func.Action;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import com.tbts.model.handlers.ExpertManager;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.criteria.Criteria;
import tigase.jaxmpp.core.client.criteria.ElementCriteria;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.AbstractStanzaModule;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.modules.roster.RosterModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.J2SEPresenceStore;
import tigase.jaxmpp.j2se.J2SESessionObject;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;

import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 04.11.15
 * Time: 20:22
 */
public class ExpertsAdminBot {
  private static final String TBTS_XMLNS = "http://toobusytosearch.net/schema";
  private static final Logger log = Logger.getLogger(ExpertsAdminBot.class.getName());
  private final Jaxmpp connection = new Jaxmpp(new J2SESessionObject());
  public static final String EXPERTS_ADMIN_LONG_PASSWORD = "experts-admin-long-password";
  private final TrackPresenceComponent sessionManager;

  private final BareJID jid;
  @SuppressWarnings("FieldCanBeLocal")
  private final Action<Expert> expertLogic;

  public ExpertsAdminBot(String name, TrackPresenceComponent sessionManager) {
    this.sessionManager = sessionManager;
    jid = BareJID.bareJIDInstance(name);
    connection.getProperties().setUserProperty(SessionObject.USER_BARE_JID, jid);
    connection.getProperties().setUserProperty(SessionObject.PASSWORD, EXPERTS_ADMIN_LONG_PASSWORD);
    connection.getSessionObject().setProperty(SocketConnector.TLS_DISABLED_KEY, true);
    PresenceModule.setPresenceStore(connection.getSessionObject(), new J2SEPresenceStore());
    connection.getModulesManager().register(new PresenceModule());
    connection.getModulesManager().register(new RosterModule());
    final SimpleParser parser = new SimpleParser();
    connection.getModulesManager().register(new AbstractStanzaModule<Message>() {
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
        final DomBuilderHandler builder = new DomBuilderHandler();
        try {
          final String text = stanza.getAsString();
          parser.parse(builder, text.toCharArray(), 0, text.length());
          processIncoming(builder.getParsedElements().poll());
        } catch (XMLException e) {
          throw new RuntimeException(e);
        }
      }
    });

    final Timer restoreConnectionTimer = new Timer("Experts admin bot connection restore thread", true);
    restoreConnectionTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        try {
          if (!connection.isConnected())
            connection.login();
        }
        catch (JaxmppException ignore) {
          ignore.printStackTrace();
        }
      }
    }, 0, TimeUnit.MINUTES.toMillis(3));
    expertLogic = new ExpertLogic();
    ExpertManager.instance().addListener(expertLogic);
  }

  private void processIncoming(Element wrappedElement) {
    System.out.println("Incoming: " + wrappedElement.toString());
    String from = wrappedElement.getAttributes().get("from");
    Element room = wrappedElement.getChild("body").getChild("room");
    if (room != null && from != null) {
      final Expert expert = ExpertManager.instance().get(JID.jidInstance(from).getBareJid().toString());
      if ("Ok".equals(room.getCData()))
        expert.steady();
    }
  }

  public String jid() {
    return jid.toString();
  }

  private class ExpertLogic implements Action<Expert> {
    final Set<String> expertsPool = new HashSet<>();
    @Override
    public void invoke(Expert expert) {
      if (!connection.isConnected()) {
        try {
          connection.login();
        } catch (JaxmppException ignore) {
          ignore.printStackTrace();
        }
      }
      Room active = expert.active();
      switch (expert.state()) {
        case AWAY:
          if (expertsPool.remove(expert.id()))
            expertsCountChanged();
          break;
        case READY:
          if (expertsPool.add(expert.id()))
            expertsCountChanged();
          break;
        case STEADY:
          break;
        case INVITE:
          try {
            final Element invite = new Element("message");
            final String subj = active.query().text();
            {
              final Element x = new Element("x");
              x.setXMLNS("http://jabber.org/protocol/muc#user");
              x.addChild(new Element("invite", new String[]{"from"}, new String[]{active.id()}));
              x.addChild(new Element("subj", subj));
              invite.addChild(x);
            }
            {
              final Element x = new Element("x");
              x.setXMLNS("jabber:x:conference");
              x.setAttribute("reason", subj);
              invite.addChild(x);
            }
            sessionManager.addPacket(Packet.packetInstance(invite, tigase.xmpp.JID.jidInstance(active.id()), tigase.xmpp.JID.jidInstance(tigase.xmpp.BareJID.bareJIDInstance(expert.id()), "expert")));
          }
          catch (TigaseStringprepException e) {
            log.log(Level.WARNING, "Error constructing invte", e);
          }
          break;
        case CHECK:
          if (active == null)
            throw new NullPointerException();
          roomStateChanged(expert, active.id(), "check", active.owner().id());
          break;
        case DENIED:
          roomStateChanged(expert, active.id(), "denied", "invitation to was canceled");
          break;
        case CANCELED:
          roomStateChanged(expert, active.id(), "canceled" , "the request was canceled");
          break;
        case GO:
          break;
      }
    }

    private void roomStateChanged(Expert expert, String roomId, String type, String body) {
      try {
        Message message = Message.create();
        message.setType(StanzaType.chat);
        message.setBody(body);
        tigase.jaxmpp.core.client.xml.Element room = ElementFactory.create("room", "", TBTS_XMLNS);
        room.setAttribute("type", type);
        room.setAttribute("id", roomId);
        message.getWrappedElement().getFirstChild().addChild(room);
        message.setTo(JID.jidInstance(expert.id()));
        connection.send(message);
      } catch (JaxmppException e) {
        throw new RuntimeException(e);
      }
    }

    private void expertsCountChanged() {
      try {
        final Presence presence = Stanza.createPresence();
        presence.setStatus(expertsPool.size() + " experts online");
        presence.setShow(Presence.Show.online);
        connection.send(presence);
      }
      catch (JaxmppException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
