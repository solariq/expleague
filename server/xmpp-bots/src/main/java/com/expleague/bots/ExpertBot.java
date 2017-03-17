package com.expleague.bots;

import com.expleague.model.Answer;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;

/**
 * User: solar
 * Date: 11.10.15
 * Time: 21:10
 */
public class ExpertBot extends Bot {

  public ExpertBot(final BareJID jid, final String passwd) throws JaxmppException {
    super(jid, passwd, "expert", "/expert");
  }

  protected ExpertBot(final BareJID jid, final String passwd, String resource, String email) {
    super(jid, passwd, resource, email);
  }

  public void sendOk(BareJID roomJID) throws JaxmppException {
    final Element okElem = ElementFactory.create("ok");
    okElem.setXMLNS(TBTS_XMLNS);
    sendToGroupChat(okElem, JID.jidInstance(roomJID));
  }

  public void sendStart(BareJID roomJID) throws JaxmppException {
    final Element startElem = ElementFactory.create("start");
    startElem.setXMLNS(TBTS_XMLNS);
    sendToGroupChat(startElem, JID.jidInstance(roomJID));
  }

  public void sendAnswer(BareJID roomJID, Answer answer) throws JaxmppException {
    final Element answerElem = ElementFactory.create("answer");
    answerElem.setXMLNS(TBTS_XMLNS);
    answerElem.setValue(answer.value());
    sendToGroupChat(answerElem, JID.jidInstance(roomJID));
  }
}
