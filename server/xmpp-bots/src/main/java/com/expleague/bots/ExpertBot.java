package com.expleague.bots;

import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;

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
    sendToGroupChat(okElem, offer.getFrom());
  }

  public void sendStart(BareJID roomJID) throws JaxmppException {
    final Element startElem = ElementFactory.create("start");
    startElem.setXMLNS(TBTS_XMLNS);
    sendToGroupChat(startElem, JID.jidInstance(roomJID));
  }

  public void sendAnswer(BareJID roomJID, String answer) throws JaxmppException {
    final Element answerElem = ElementFactory.create("answer");
    answerElem.setXMLNS(TBTS_XMLNS);
    answerElem.setValue(answer);
    sendToGroupChat(answerElem, JID.jidInstance(roomJID));
  }

  public void sendCancel(BareJID roomJID) throws JaxmppException {
    final Element cancelElem = ElementFactory.create("cancel");
    cancelElem.setXMLNS(TBTS_XMLNS);
    sendToGroupChat(cancelElem, JID.jidInstance(roomJID));
  }
}
