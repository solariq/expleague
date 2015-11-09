package com.tbts.bots;

import com.spbsu.commons.util.sync.StateLatch;
import com.spbsu.commons.xml.JDOMUtil;
import org.jdom2.Element;
import org.jdom2.Namespace;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;

/**
 * User: solar
 * Date: 11.10.15
 * Time: 21:10
 */
public class ExpertBot extends Bot {

  public static final String TBTS_XMLNS = "http://toobusytosearch.net/schema";
  private BareJID roomJID;

  public ExpertBot(final BareJID jid, final String passwd) throws JaxmppException {
    super(jid, passwd, "expert");
    jaxmpp.getEventBus().addHandler(MucModule.InvitationReceivedHandler.InvitationReceivedEvent.class, (sessionObject, invitation, id, bareJID) -> {
      onInvite(invitation);
    });
  }

  public void onInvite(MucModule.Invitation invitation) {
    System.out.println("Invitation received from " + invitation.getInviterJID() + " to " + invitation.getRoomJID() + " on topic [" + invitation.getReason() + "]");
  }


  public static void main(final String[] args) throws JaxmppException, InterruptedException {
    final StateLatch latch = new StateLatch();
    final ExpertBot expert = new ExpertBot(BareJID.bareJIDInstance("expert-bot-1", "toobusytosearch.net"), "poassord") {
      @Override
      public void onInvite(MucModule.Invitation invitation) {
        accept(invitation);
        latch.advance();
      }
    };

    expert.start();
//    expert.offline();
    expert.online();
    latch.state(2, 1);
    expert.answer("Otvali!");
    Thread.sleep(20000);
    expert.offline();
    expert.stop();
  }

  public void offline() {
    try {
      final Presence presence = Presence.create();
      presence.setShow(Presence.Show.xa);
      jaxmpp.send(presence);
      System.out.println("Sent offline presence");
    } catch (JaxmppException e) {
      throw new RuntimeException(e);
    }
  }

  public void answer(String text) {
    try {
      System.out.println("Answering to the room");
      final Message msg = Message.create();
      msg.setTo(JID.jidInstance(roomJID));
      msg.setType(StanzaType.groupchat);
      msg.setBody(text);
      jaxmpp.send(msg);
    } catch (JaxmppException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void onMessage(String asString) {
    Element element = JDOMUtil.parseXml(asString);
    Element room = element.getChild("body", Namespace.getNamespace("jabber:client")).getChild("room", Namespace.getNamespace(TBTS_XMLNS));
    if (room != null) {
      System.out.println("Answering to the room");
      try {
        final Message msg = Message.create();
        msg.setTo(JID.jidInstance(element.getAttributeValue("from")));
        msg.setType(StanzaType.chat);
        msg.setBody("");
        tigase.jaxmpp.core.client.xml.Element roomE = ElementFactory.create("room", "", TBTS_XMLNS);
        roomE.setAttribute("type", "check");
        roomE.setAttribute("id", room.getAttributeValue("id"));
        roomE.setValue("Ok");
        msg.getWrappedElement().getFirstChild().addChild(roomE);
        jaxmpp.send(msg);
      } catch (JaxmppException e) {
        throw new RuntimeException(e);
      }
    }
    super.onMessage(asString);
  }

  public void accept(MucModule.Invitation invitation) {
    try {
      final Presence presence = Presence.create();
      presence.setShow(Presence.Show.online);
      roomJID = invitation.getRoomJID();
      presence.setTo(JID.jidInstance(roomJID, "expert"));
      jaxmpp.send(presence);
    } catch (JaxmppException e) {
      throw new RuntimeException(e);
    }
  }
}
