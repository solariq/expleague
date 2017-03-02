package com.expleague.bots;

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;

/**
 * User: solar
 * Date: 11.10.15
 * Time: 21:10
 */
public class ExpertBot extends Bot {

  public ExpertBot(final BareJID jid, final String passwd) throws JaxmppException {
    super(jid, passwd, "expert", "/expert");
  }

  public void sendOk(Message offer) throws JaxmppException {
    final Element okElem = ElementFactory.create("ok");
    okElem.setXMLNS(TBTS_XMLNS);

    final Message message = Message.create();
    message.addChild(okElem);
    message.setTo(offer.getFrom());
    jaxmpp.send(message);
  }

  public void sendStart(BareJID roomJID) throws JaxmppException {
    final Element startElem = ElementFactory.create("start");
    startElem.setXMLNS(TBTS_XMLNS);

    final Message message = Message.create();
    message.addChild(startElem);
    message.setType(StanzaType.groupchat);
    message.setTo(JID.jidInstance(roomJID));
    jaxmpp.send(message);
  }

  public void sendAnswer(BareJID roomJID, String answer) throws JaxmppException {
    final Element answerElem = ElementFactory.create("answer");
    answerElem.setXMLNS(TBTS_XMLNS);
    answerElem.setValue(answer);

    final Message message = Message.create();
    message.addChild(answerElem);
    message.setType(StanzaType.groupchat);
    message.setTo(JID.jidInstance(roomJID));
    jaxmpp.send(message);
  }
}
