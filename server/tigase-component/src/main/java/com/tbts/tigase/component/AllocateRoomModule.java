package com.tbts.tigase.component;

import com.spbsu.commons.func.Action;
import com.tbts.model.Answer;
import com.tbts.model.Client;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import com.tbts.model.handlers.ClientManager;
import com.tbts.model.handlers.ExpertManager;
import com.tbts.model.handlers.Reception;
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
  private static final Criteria CRIT = ElementCriteria.name("message").add(ElementCriteria.name("subject"));

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
              write(Packet.packetInstance(invite, JID.jidInstance(active.id()), JID.jidInstance(BareJID.bareJIDInstance(expert.id()), "expert")));
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
    final BareJID to = packet.getStanzaTo().getBareJID();
    final Room room = Reception.instance().room(to.toString());

    final JID stanzaFrom = packet.getStanzaFrom();
    if (room != null && stanzaFrom != null) {
      final boolean isExpert = "expert".equals(stanzaFrom.getResource());
      final String fromId = stanzaFrom.getBareJID().toString();
      if ("message".equals(packet.getElement().getName())) {
        room.onMessage(fromId, packet.getElement().toStringPretty());
      }

      final Client client = isExpert ? null : ClientManager.instance().get(fromId);
      final Expert expert = isExpert ? ExpertManager.instance().get(fromId) : null;

      if (client != null && CRIT.match(packet.getElement())) {
        client.query();
      }
      else if (expert != null && expert.state() == Expert.State.GO) {
        expert.answer(new Answer());
      }
    }

    super.process(packet);
  }
}
