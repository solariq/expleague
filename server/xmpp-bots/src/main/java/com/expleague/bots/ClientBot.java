package com.expleague.bots;

import com.expleague.model.Offer;
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

  public BareJID startRoom(String topic, double started, Offer.Urgency urgency, Offer.Location location, String imageUrl) throws JaxmppException {
    final Element offerElem = ElementFactory.create("offer");
    offerElem.setXMLNS(TBTS_XMLNS);
    offerElem.setAttribute("client", jid().toString());
    offerElem.setAttribute("urgency", urgency.name().toLowerCase());
    offerElem.setAttribute("started", Double.toString(started));

    final Element topicElem = offerElem.addChild(ElementFactory.create("topic"));
    topicElem.setValue(topic);
    final Element locationElem = offerElem.addChild(ElementFactory.create("location"));
    locationElem.setAttribute("longitude", Double.toString(location.longitude()));
    locationElem.setAttribute("latitude", Double.toString(location.latitude()));

    final Element imageElem = offerElem.addChild(ElementFactory.create("image"));
    imageElem.setValue(imageUrl);

    final BareJID room = BareJID.bareJIDInstance(jid().getLocalpart() + "-room-" + (int) (System.currentTimeMillis() / 1000), "muc." + jid().getDomain());
    final Message message = Message.create();
    message.addChild(offerElem);
    message.setTo(JID.jidInstance(room));
    jaxmpp.send(message);
    return room;
  }
}