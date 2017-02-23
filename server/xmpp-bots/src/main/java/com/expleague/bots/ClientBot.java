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
public class ClientBot extends Bot {
  public ClientBot(final BareJID jid, final String passwd) throws JaxmppException {
    super(jid, passwd, "client");
  }

  public BareJID startRoom(String topic) throws JaxmppException {
    final Element offerElem = ElementFactory.create("offer");
    offerElem.setXMLNS(TBTS_XMLNS);
    offerElem.setAttribute("client", jid().toString());
    offerElem.setAttribute("local", "false");
    offerElem.setAttribute("urgency", "day");
    offerElem.setAttribute("started", Long.toString(System.currentTimeMillis()));

    final Element topicElem = offerElem.addChild(ElementFactory.create("topic"));
    topicElem.setValue(topic);
    final Element locationElem = offerElem.addChild(ElementFactory.create("location"));
    locationElem.setAttribute("longitude", "30.32538469883643");
    locationElem.setAttribute("latitude", "59.98062295379115");

    final BareJID room = BareJID.bareJIDInstance(jid().getLocalpart() + "-room-" + (int) (System.currentTimeMillis() / 1000), "muc." + jid().getDomain());
    final Message message = Message.create();
    message.addChild(offerElem);
    message.setTo(JID.jidInstance(room));
    jaxmpp.send(message);
    return room;
  }
}