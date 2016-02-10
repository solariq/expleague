package com.expleague.bots;

import com.spbsu.commons.util.sync.StateLatch;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;
import tigase.jaxmpp.j2se.xml.J2seElement;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;

/**
 * User: solar
 * Date: 11.10.15
 * Time: 21:10
 */
public class ExpertBot extends Bot {

  public static final String TBTS_XMLNS = "http://expleague.com/scheme";
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
    final ExpertBot expert = new ExpertBot(BareJID.bareJIDInstance("expert-bot-1", "localhost"), "poassord") {
      @Override
      public void onInvite(MucModule.Invitation invitation) {
        accept(invitation);
        latch.advance();
      }
    };

    expert.start();
//    expert.offline();
    latch.state(2, 1);
    expert.answer("Otvali!");
    expert.online();
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
    final DomBuilderHandler handler = new DomBuilderHandler();
    new SimpleParser().parse(handler, asString.toCharArray(), 0, asString.length());
    final Element element = handler.getParsedElements().peek();

    Element offer = element.getChild("offer", TBTS_XMLNS);
    if (offer != null) {
      System.out.println("Answering to the room");
      try {
        final Element msg = new Element("message");
        msg.setXMLNS("jabber:client");
        msg.setAttribute("to", element.getAttributeStaticStr("from"));
        msg.setAttribute("type", StanzaType.chat.name());
        msg.addChild(offer);
        final Element ok = new Element("ok");
        ok.setXMLNS(TBTS_XMLNS);
        msg.setXMLNS("jabber:client");
        msg.addChild(ok);
        jaxmpp.send(Message.create(new J2seElement(msg)));
        final Presence stanza = Presence.create();
        stanza.setTo(JID.jidInstance(element.getAttributeStaticStr("from")));
        jaxmpp.send(stanza);
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
