package com.tbts.tigase.component;

import com.spbsu.commons.func.Action;
import com.tbts.dao.DynamoDBArchive;
import com.tbts.dao.MySQLDAO;
import com.tbts.model.Client;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import com.tbts.model.handlers.*;
import tigase.conf.ConfigurationException;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.db.AuthRepository;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;
import tigase.db.UserRepository;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.J2SEPresenceStore;
import tigase.jaxmpp.j2se.J2SESessionObject;
import tigase.jaxmpp.j2se.Jaxmpp;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.server.xmppsession.SessionManager;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.*;
import tigase.xmpp.impl.roster.RosterAbstract;
import tigase.xmpp.impl.roster.RosterFactory;

import javax.script.Bindings;
import java.util.Map;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 24.09.15
 * Time: 22:31
 */
public class TrackPresenceComponent extends SessionManager {
  private static final Logger log = Logger.getLogger(TrackPresenceComponent.class.getName());
  public static final String EXPERTS_ADMIN_LONG_PASSWORD = "experts-admin-long-password";
  private static Criteria CRIT = ElementCriteria.name("presence");

  @SuppressWarnings("FieldCanBeLocal")
  private final Action<Expert> expertCommunication;
  private final Jaxmpp jaxmppServer = new Jaxmpp(new J2SESessionObject());

  private int expertsCount = -1;
  private AuthRepository authRepo;
  private UserRepository userRepo;

  public TrackPresenceComponent() {
    super();
    expertCommunication = expert -> {
      switch (expert.state()) {
        case AWAY:
        case READY:
          int expertsCount = ExpertManager.instance().count();
          if (expertsCount != TrackPresenceComponent.this.expertsCount) {
            try {
              final BareJID admin = BareJID.bareJIDInstance("experts-admin@" + getDefVHostItem().getDomain());
              if (!jaxmppServer.isConnected()) { // create admin user if necessarily
                if (!userRepo.userExists(admin))
                  authRepo.addUser(admin, EXPERTS_ADMIN_LONG_PASSWORD);
                tigase.jaxmpp.core.client.BareJID jid = tigase.jaxmpp.core.client.BareJID.bareJIDInstance(admin.toString());
                jaxmppServer.getProperties().setUserProperty(SessionObject.USER_BARE_JID, jid);
                jaxmppServer.getProperties().setUserProperty(SessionObject.PASSWORD, EXPERTS_ADMIN_LONG_PASSWORD);
                jaxmppServer.getSessionObject().setProperty(SocketConnector.TLS_DISABLED_KEY, true);
                PresenceModule.setPresenceStore(jaxmppServer.getSessionObject(), new J2SEPresenceStore());
                jaxmppServer.getModulesManager().register(new PresenceModule());
                jaxmppServer.login();
              }
              { // confirm that all users have admin as the roster buddy
                final JID adminBuddy = JID.jidInstance(admin);
                final RosterAbstract roster = RosterFactory.getRosterImplementation(true);
                final XMPPResourceConnection adminConnection = getSession(admin).getResourceConnection(adminBuddy);
                for (final String jid : ClientManager.instance().online()) {
                  final JID clientBuddy = JID.jidInstance(jid);
                  if (!roster.containsBuddy(adminConnection, clientBuddy)) {
                    roster.addBuddy(adminConnection, clientBuddy, jid, new String[0], "");
                    roster.setBuddySubscription(adminConnection, RosterAbstract.SubscriptionType.both, clientBuddy);
                  }
                }
                for (final BareJID jid : userRepo.getUsers()) {
                  final XMPPSession session = getSession(jid);
                  if (session != null) {
                    for (final XMPPResourceConnection connection : session.getActiveResources()) {
                      if (!roster.containsBuddy(connection, adminBuddy)) {
                        roster.addBuddy(connection, adminBuddy, "Administator", new String[0], "");
                        roster.setBuddySubscription(connection, RosterAbstract.SubscriptionType.both, adminBuddy);
                      }
                    }
                  }
                }
              }
              { // sending presence with experts count
                final tigase.jaxmpp.core.client.xmpp.stanzas.Presence presence = Stanza.createPresence();
                presence.setStatus(expertsCount + " experts online");
                presence.setShow(tigase.jaxmpp.core.client.xmpp.stanzas.Presence.Show.online);
                jaxmppServer.send(presence);
                System.out.println("Presence sent");
              }
            }
            catch (TigaseDBException | NotAuthorizedException | JaxmppException | TigaseStringprepException e) {
              throw new RuntimeException(e);
            }
            finally {
              TrackPresenceComponent.this.expertsCount = expertsCount;
            }
          }
          break;
        case CHECK:
          break;
        case STEADY:
          break;
        case INVITE:
          break;
        case DENIED:
          break;
        case CANCELED:
          break;
        case GO:
          break;
      }
    };
    ExpertManager.instance().addListener(expertCommunication);
  }

  @SuppressWarnings("unused")
  private static StatusTracker tracker = new StatusTracker(System.out);
  @Override
  public void setProperties(Map<String, Object> props) throws ConfigurationException {
    super.setProperties(props);
    if (Archive.instance == null)
      Archive.instance = new DynamoDBArchive();
    if (DAO.instance == null && props.containsKey("tbtsdb-connection")) {
      DAO.instance = new MySQLDAO((String) props.get("tbtsdb-connection"), jid -> {
        //noinspection unchecked
        final Map<JID, XMPPResourceConnection> connections = (Map<JID, XMPPResourceConnection>)bindings.get("userConnections");
        for (final XMPPResourceConnection connection : connections.values()) {
          try {
            if (jid.equals(connection.getBareJID().toString()))
              return true;
          }
          catch (NotAuthorizedException ignore) {}
        }
        return false;
      });
      DAO.instance.init();
    }
    authRepo = (AuthRepository) props.get(RepositoryFactory.SHARED_AUTH_REPO_PROP_KEY);
    userRepo = (UserRepository) props.get(RepositoryFactory.SHARED_USER_REPO_PROP_KEY);
  }

  private Bindings bindings;
  @Override
  public void initBindings(Bindings binds) {
    super.initBindings(binds);
    bindings = binds;
  }

  @Override
  protected void closeSession(XMPPResourceConnection conn, boolean closeOnly) {
    try {
      final BareJID bareJID = conn.getBareJID();
      final Expert expert = ExpertManager.instance().get(bareJID.toString());
      if (expert != null)
        expert.online(false);
    }
    catch (NotAuthorizedException ignore) {}
    super.closeSession(conn, closeOnly);
  }

  @Override
  public void processPacket(Packet packet) {
    super.processPacket(packet);
    if (!CRIT.match(packet.getElement()))
      return;
    final JID from = packet.getStanzaFrom();
    final JID to = packet.getStanzaTo();
    if (from == null) {
      log.warning("Invalid package: " + packet.toString());
      return;
    }
    final Status status = statusFromPacket(packet);
    if (updateExpertState(from, to, status) == null)
      updateClientState(from, to, status);
  }

  private void updateClientState(JID from, JID to, Status status) {
    final Client client = ClientManager.instance().get(from.getBareJID().toString());
    if (client == null)
      return;
    final Room room = to != null ? Reception.instance().room(client, to.getBareJID().toString()) : null;
    if (room != null && room.state() == Room.State.INIT) {
      final Element unlock = new Element(Iq.ELEM_NAME);
      final Element query = new Element("query");
      query.setXMLNS("http://jabber.org/protocol/muc#owner");
      final Element x = new Element("x");
      x.setAttribute("type", "submit");
      query.addChild(x);
      unlock.addChild(query);
      addPacket(Packet.packetInstance(unlock, from, to));
      room.open();
    }

    switch (status) {
      case WAITING:
        if (client.state() == Client.State.FORMULATING)
          client.query();
        break;
      case UNKNOWN:
        break;
      case TYPING:
        break;
      case AVAILABLE:
        client.presence(true);
        if (room != null) {
          client.activate(room);
          client.formulating();
        }
        break;
      case UNAVAILABLE:
        if (to == null)
          client.presence(false);
        else
          client.activate(null);
        break;
    }
  }

  private Expert updateExpertState(JID from, JID to, Status status) {
    Expert expert = ExpertManager.instance().get(from.getBareJID().toString());
    if ("expert".equals(from.getResource()) && !from.getDomain().startsWith("muc.") && expert == null)
      expert = ExpertManager.instance().register(from.getBareJID().toString());
    if (expert == null)
      return null;
    final Room room = to != null ? Reception.instance().room(to.getBareJID().toString()) : null;
    if (status == Status.UNAVAILABLE && room == null)
      expert.online(false);
    switch (expert.state()) {
      case CHECK:
        if (room != null && room.equals(expert.active()))
          expert.steady();
        break;
      case INVITE:
        if (room != null && room.equals(expert.active()))
          expert.ask(room);
        break;
      case DENIED:
        expert.online(true);
        break;
      case CANCELED:
        expert.online(true);
        break;
      case GO:
        break;
      case AWAY:
        if (status != Status.UNAVAILABLE)
          expert.online(true);
        break;
    }
    return expert;
  }

  public enum Status {
    AVAILABLE,
    UNAVAILABLE,
    TYPING,
    WAITING,
    STEADY, UNKNOWN
  }

  public Status statusFromPacket(Packet packet) {
    final Map<String, String> attrs = packet.getElement().getAttributes();
    if ("unavailable".equals(attrs.get("type")))
      return Status.UNAVAILABLE;
    if ("available".equals(attrs.get("type")))
      return Status.AVAILABLE;
    final Element show = packet.getElement().getChild("show");
    if (show != null) {
      if ("chat".equalsIgnoreCase(show.childrenToString()))
        return Status.AVAILABLE;
      if ("steady".equalsIgnoreCase(show.childrenToString()))
        return Status.STEADY;
      if ("away".equalsIgnoreCase(show.childrenToString()))
        return Status.UNAVAILABLE;
      if ("typing".equalsIgnoreCase(show.childrenToString()))
        return Status.UNAVAILABLE;
      if ("waiting".equalsIgnoreCase(show.childrenToString()))
        return Status.UNAVAILABLE;
      if ("xa".equalsIgnoreCase(show.childrenToString()))
        return Status.UNAVAILABLE;
    }
    else if (attrs.get("type") == null)
      return Status.AVAILABLE;
    log.warning("Unknown user state. Type: " + attrs.get("type") + (show != null ? " show: " + show.toString() : ""));
    return Status.UNKNOWN;
  }
}
