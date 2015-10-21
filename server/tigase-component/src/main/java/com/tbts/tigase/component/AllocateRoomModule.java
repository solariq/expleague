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

import java.util.List;
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
  @SuppressWarnings("unused")
  private static StatusTracker tracker = new StatusTracker(System.out);
  @SuppressWarnings("FieldCanBeLocal")
  private final Action<Expert> expertCommunication;
  private int expertsCount = 0;
  public AllocateRoomModule() {
    expertCommunication = new Action<Expert>() {
      @Override
      public void invoke(Expert expert) {
        switch (expert.state()) {
          case INVITE:
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
            break;
          case CHECK:
            expert.steady();
            break;
          case AWAY:
          case READY:
            int expertsCount = ExpertManager.instance().count();
            if (expertsCount != AllocateRoomModule.this.expertsCount) {
              AllocateRoomModule.this.expertsCount = expertsCount;
              final Element presence = new Element("message");
              presence.setXMLNS("jabber:client");
              final Element show = new Element("body");
              show.setCData(expertsCount + " experts online");
              presence.addChild(show);

              final List<String> online = ClientManager.instance().online();
              for (final String bareJID : online) {
                try {
                  write(Packet.packetInstance(presence, JID.jidInstance(context.getServiceName().getDomain()), JID.jidInstance(bareJID)));
                } catch (TigaseStringprepException e) {
                  throw new RuntimeException(e);
                }
              }
            }
            break;
        }
      }
    };
    ExpertManager.instance().addListener(expertCommunication);
  }

  @Override
  public void process(Packet packet) throws MUCException {
    if (CRIT.match(packet.getElement())) {
      final Client client = ClientManager.instance().byJID(packet.getStanzaFrom().getBareJID().toString());
      final String subject = packet.getElement().getChild(SUBJECT).childrenToString();
      Room room = Reception.instance().room(client, packet.getStanzaTo().getBareJID().toString());
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

    if ("message".equals(packet.getElement().getName())) {
      Reception.instance().archive().log(packet.getElement());
    }
    super.process(packet);
  }
}
