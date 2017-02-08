package com.expleague.bots;

import com.spbsu.commons.util.sync.StateLatch;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.Connector;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xmpp.stanzas.IQ;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;

/**
 * User: solar
 * Date: 11.10.15
 * Time: 21:10
 */
public class ClientBot extends Bot {
  private BareJID activeRoom;

  public ClientBot(final BareJID jid, final String passwd) throws JaxmppException {
    super(jid, passwd, "client");
  }

  public BareJID startRoom() throws JaxmppException {
    final StateLatch latch = new StateLatch();
    final BareJID room = BareJID.bareJIDInstance(jid().getLocalpart() + "-room-" + (int)(System.currentTimeMillis() / 1000), "muc." + jid().getDomain());

    final IQ iq = IQ.create();
    iq.setAttribute("type", "set");
    final Element query = ElementFactory.create("query");
    query.setXMLNS("http://jabber.org/protocol/muc#owner");
    final Element x = query.addChild(ElementFactory.create("x"));
    x.setXMLNS("jabber:x:data");
    x.setAttribute("type", "submit");
    iq.addChild(query);
    iq.setTo(JID.jidInstance(room));

    Connector.StanzaReceivedHandler handler = (sessionObject, stanza) -> {
      try {
        if (stanza instanceof Message && latch.state() == 1) {
          Element body = stanza.getWrappedElement().getFirstChild("body");
          if (body != null && body.getValue().contains("unlocked")) {
            System.out.println("Room created & unlocked");
            latch.state(2);
          }
        }
      } catch (JaxmppException e) {
        throw new RuntimeException(e);
      }
    };
    jaxmpp.getEventBus().addHandler(Connector.StanzaReceivedHandler.StanzaReceivedEvent.class, handler);
    jaxmpp.send(iq);
    latch.state(2, 1);
    jaxmpp.getEventBus().remove(Connector.StanzaReceivedHandler.StanzaReceivedEvent.class, handler);
    activeRoom = room;
    return room;
  }

  public void topic(String topic) {
    if (activeRoom == null)
      throw new IllegalStateException();
    try {
      final Message message = Message.create();
      message.setType(StanzaType.groupchat);
      message.addChild(ElementFactory.create("subject", topic, null));
      message.setTo(JID.jidInstance(activeRoom));
      System.out.println(message.getAsString());
      jaxmpp.send(message);
    } catch (JaxmppException e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(final String[] args) throws JaxmppException {
    final ClientBot client = new ClientBot(BareJID.bareJIDInstance("client-bot-1", "localhost"), "poassord");
    client.start();
    client.online();
    client.startRoom();
    client.topic("Hello world");
    client.stop();
  }
}
