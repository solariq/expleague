package com.tbts.tigase.component;

import com.tbts.experts.ExpertManager;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.server.Packet;
import tigase.server.xmppsession.SessionManager;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.Map;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 24.09.15
 * Time: 22:31
 */
public class TrackExpertsComponent extends SessionManager {
  private static final Logger log = Logger.getLogger(TrackExpertsComponent.class.getName());
  private static Criteria CRIT = ElementCriteria.name("presence");

  @Override
  public void processPacket(Packet packet) {
    super.processPacket(packet);
    if (!CRIT.match(packet.getElement()))
      return;
    final JID from = packet.getStanzaFrom();
    log.warning(from.toString());
    final BareJID bareJID = from.getBareJID();
    if ("expert".equals(from.getResource())) {
      final Element show = packet.getElement().getChild("show");
      final Map<String, String> attrs = packet.getElement().getAttributes();
      if ("available".equals(attrs.get("type"))
              || (!attrs.containsKey("type") && show == null)
              || (show != null && "chat".equalsIgnoreCase(show.childrenToString()))) {
        ExpertManager.instance().get(bareJID).online(true);
        return;
      }
    }
    ExpertManager.instance().get(bareJID).online(false);
  }
}
