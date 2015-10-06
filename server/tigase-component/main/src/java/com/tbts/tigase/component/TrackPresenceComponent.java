package com.tbts.tigase.component;

import com.tbts.model.Client;
import com.tbts.model.Expert;
import com.tbts.model.Reception;
import com.tbts.model.Room;
import com.tbts.model.clients.ClientManager;
import com.tbts.model.experts.ExpertManager;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.server.Packet;
import tigase.server.xmppsession.SessionManager;
import tigase.xml.Element;
import tigase.xmpp.JID;

import java.util.Map;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 24.09.15
 * Time: 22:31
 */
public class TrackPresenceComponent extends SessionManager {
  private static final Logger log = Logger.getLogger(TrackPresenceComponent.class.getName());
  private static Criteria CRIT = ElementCriteria.name("presence");

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
    final Client client = ClientManager.instance().byJID(from.getBareJID());
    if (client == null)
      return;
    final Room room = to != null ? Reception.instance().room(client, to.getBareJID()) : null;
    if (room != null)
      client.activate(room);
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
        if (room == null)
          client.presence(true);
        else if (client.state() == Client.State.ONLINE)
          client.formulating();
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
    Expert expert = ExpertManager.instance().get(from.getBareJID());
    if ("expert".equals(from.getResource()) && expert == null)
      expert = ExpertManager.instance().register(from.getBareJID());
    if (expert == null)
      return null;
    final Room room = to != null ? Reception.instance().room(to.getBareJID()) : null;
    if (status == Status.UNAVAILABLE)
      expert.online(false);
    switch (expert.state()) {
      case CHECK:
        if (room != null && room.equals(expert.active()))
          expert.steady();
        break;
      case INVITE:
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
    UNKNOWN
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
