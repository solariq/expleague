package com.tbts.tigase.component;

import com.tbts.tigase.component.com.tbts.experts.ExpertManager;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.exceptions.MUCException;
import tigase.muc.modules.GroupchatMessageModule;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 23.09.15
 * Time: 14:46
 */
public class AllocateRoomModule extends GroupchatMessageModule {
  private static final Logger log = Logger.getLogger(AllocateRoomModule.class.getName());
  private static final String SUBJECT = "subject";
  Criteria CRIT = ElementCriteria.name("message").add(ElementCriteria.name(SUBJECT));

  @Override
  public void process(Packet packet) throws MUCException {
    if (CRIT.match(packet.getElement())) {
      final BareJID expert = ExpertManager.instance().nextAvailable();
      try {
        final Element invite = new Element(Message.ELEM_NAME);
        final Element x = new Element("x");
        x.setXMLNS("http://jabber.org/protocol/muc#user");
        x.addChild(new Element("invite", new String[]{"from"}, new String[]{packet.getStanzaFrom().toString()}));
        invite.addChild(x);
        final String subj= packet.getElement().getChild(SUBJECT).getCData();
        x.addChild(new Element("subj", subj));
        write(Packet.packetInstance(invite, packet.getStanzaTo(), JID.jidInstance(expert, "expert")));
      } catch (TigaseStringprepException e) {
        log.log(Level.WARNING, "Error constructing invte", e);
      }
    }
    super.process(packet);
  }
}
