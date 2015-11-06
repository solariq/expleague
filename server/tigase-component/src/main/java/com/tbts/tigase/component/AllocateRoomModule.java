package com.tbts.tigase.component;

import com.tbts.model.Client;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import com.tbts.model.handlers.ClientManager;
import com.tbts.model.handlers.ExpertManager;
import com.tbts.model.handlers.Reception;
import tigase.component.Context;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.exceptions.MUCException;
import tigase.muc.modules.GroupchatMessageModule;
import tigase.server.Packet;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.logging.Logger;

/**
 * User: solar
 * Date: 23.09.15
 * Time: 14:46
 */
public class AllocateRoomModule extends GroupchatMessageModule {
  private static final Logger log = Logger.getLogger(AllocateRoomModule.class.getName());
  private static final Criteria CRIT = ElementCriteria.name("message").add(ElementCriteria.name("subject"));

  public AllocateRoomModule() {
  }

  @Override
  public void setContext(Context context) {
    super.setContext(context);
//    context.getEventBus().addHandler(VHostListener.);
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
        room.answer();
      }
    }

    super.process(packet);
  }
}
