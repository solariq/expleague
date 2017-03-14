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

  public BareJID startRoom(String topic, String urgency, long started, double longitude, double latitude, String imageSrc) throws JaxmppException {
    final Element offerElem = ElementFactory.create("offer");
    offerElem.setXMLNS(TBTS_XMLNS);
    offerElem.setAttribute("client", jid().toString());
    offerElem.setAttribute("urgency", urgency);
    offerElem.setAttribute("started", Long.toString(started));

    final Element topicElem = offerElem.addChild(ElementFactory.create("topic"));
    topicElem.setValue(topic);
    final Element locationElem = offerElem.addChild(ElementFactory.create("location"));
    locationElem.setAttribute("longitude", Double.toString(longitude));
    locationElem.setAttribute("latitude", Double.toString(latitude));

    final Element imageElem = offerElem.addChild(ElementFactory.create("image"));
    imageElem.setValue(imageSrc);

    final BareJID room = BareJID.bareJIDInstance(jid().getLocalpart() + "-room-" + (int) (System.currentTimeMillis() / 1000), "muc." + jid().getDomain());
    final Message message = Message.create();
    message.addChild(offerElem);
    message.setTo(JID.jidInstance(room));
    jaxmpp.send(message);
    return room;
  }

  public void sendFeedback(BareJID roomJID, int stars, String payment) throws JaxmppException {
    final Element feedbackElem = ElementFactory.create("feedback");
    feedbackElem.setXMLNS(TBTS_XMLNS);
    feedbackElem.setAttribute("stars", Integer.toString(stars));
    feedbackElem.setAttribute("payment", payment);
    sendToGroupChat(feedbackElem, JID.jidInstance(roomJID));
  }
}