package com.tbts.tigase.component;

import com.spbsu.commons.func.Action;
import com.tbts.model.*;
import com.tbts.model.clients.ClientManager;
import com.tbts.model.experts.ExpertManager;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.exceptions.MUCException;
import tigase.muc.modules.GroupchatMessageModule;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
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
  private static final Criteria CRIT = ElementCriteria.name("message").add(ElementCriteria.name("subject"));
  private final Action<Expert> expertCommunication;

  public AllocateRoomModule() {
    expertCommunication = new Action<Expert>() {
      @Override
      public void invoke(Expert expert) {
        if (expert.state() == Expert.State.INVITE) {
          try {
            Room active = expert.active();
            final Element invite = new Element(Message.ELEM_NAME);
            final Element x = new Element("x");
            x.setXMLNS("http://jabber.org/protocol/muc#user");
            x.addChild(new Element("invite", new String[]{"from"}, new String[]{active.id()}));
            invite.addChild(x);
            final String subj = active.query().text();
            x.addChild(new Element("subj", subj));
            write(Packet.packetInstance(invite, JID.jidInstance(active.id()), JID.jidInstance(expert.id(), "expert")));
          } catch (TigaseStringprepException e) {
            log.log(Level.WARNING, "Error constructing invte", e);
          }
        } else if (expert.state() == Expert.State.CHECK) { // skip check phase
          expert.steady();
        }
      }
    };
    ExpertManager.instance().addListener(expertCommunication);
  }

  @Override
  public void process(Packet packet) throws MUCException {
    if (CRIT.match(packet.getElement())) {
      final Client client = ClientManager.instance().byJID(packet.getStanzaFrom().getBareJID());
      final String subject = packet.getElement().getChild(SUBJECT).childrenToString();
      Room room = Reception.instance().room(client, packet.getStanzaTo().getBareJID());
      client.activate(room);
      room.text(subject);
      client.query();
    }
    else {
      final Expert expert = ExpertManager.instance().get(packet.getStanzaFrom().getBareJID());
      if (expert != null && expert.state() == Expert.State.GO) {
        expert.answer(new Answer());
      }
    }
    super.process(packet);
  }
}
