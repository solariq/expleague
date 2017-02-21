package com.expleague.bots;

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;

/**
 * User: Artem
 * Date: 14.02.2017
 * Time: 16:27
 */
public class AdminBot extends Bot {
  public AdminBot(final BareJID jid, final String passwd) throws JaxmppException {
    super(jid, passwd, "expert", "/admin/expert");
  }

  public void startWorkState(BareJID roomJID) throws JaxmppException {
    final Element offerElem = ElementFactory.create("offer");
    offerElem.setXMLNS(TBTS_XMLNS);
    offerElem.setAttribute("room", roomJID.toString());

    final Message message = Message.create();
    message.addChild(offerElem);
    message.setTo(JID.jidInstance(roomJID));
    jaxmpp.send(message);
  }

  public static void main(final String[] args) throws JaxmppException, InterruptedException {
    final AdminBot admin = new AdminBot(BareJID.bareJIDInstance("expert-bot-1", "localhost"), "poassord");
    admin.start();
    admin.online();

    final Message message = admin.getReceivedMessage();
    System.out.println(message.getAsString());

    admin.stop();
  }
}