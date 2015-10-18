package com.tbts.bots;

import com.spbsu.commons.util.sync.StateLatch;
import tigase.jaxmpp.core.client.*;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xmpp.stanzas.*;
import tigase.jaxmpp.j2se.connectors.socket.SocketConnector;

import java.util.ArrayList;
import java.util.List;

/**
 * User: solar
 * Date: 11.10.15
 * Time: 21:10
 */
public class ClientBot extends Bot {
  private final List<BareJID> rooms = new ArrayList<>();
  private BareJID activeRoom;

  public ClientBot(final BareJID jid, final String passwd) throws JaxmppException {
    super(jid, passwd, "client");
    jaxmpp.getProperties().setUserProperty(SessionObject.DOMAIN_NAME, "localhost");
    jaxmpp.getSessionObject().setProperty(SocketConnector.TLS_DISABLED_KEY, true);
  }

  public BareJID startRoom() throws JaxmppException {
    final Presence presence = Presence.create();
    final StateLatch latch = new StateLatch();
    presence.setShow(Presence.Show.online);
    final BareJID room = BareJID.bareJIDInstance(jid().getLocalpart() + "-room-" + (int)(System.currentTimeMillis() / 1000), "muc." + jid().getDomain());
    presence.setTo(JID.jidInstance(room, "client"));
    jaxmpp.getEventBus().addHandler(Connector.StanzaReceivedHandler.StanzaReceivedEvent.class, (sessionObject, stanza) -> {
      try {
        if (stanza instanceof Message && "groupchat".equals(stanza.getAttribute("type")) && latch.state() == 1) {
          final IQ iq = IQ.create();
          iq.setAttribute("type", "set");
          final Element query = ElementFactory.create("query");
          query.setXMLNS("http://jabber.org/protocol/muc#owner");
          final Element x = query.addChild(ElementFactory.create("x"));
          x.setXMLNS("jabber:x:data");
          x.setAttribute("type", "submit");
          iq.addChild(query);
          iq.setTo(JID.jidInstance(room));
          jaxmpp.send(iq, new AsyncCallback() {
            @Override
            public void onError(Stanza responseStanza, XMPPException.ErrorCondition error) throws JaxmppException {
              throw new RuntimeException(responseStanza.getAsString());
            }

            @Override
            public void onSuccess(Stanza responseStanza) throws JaxmppException {
              latch.state(2);
            }

            @Override
            public void onTimeout() throws JaxmppException {
              throw new RuntimeException("Timeout on room creation");
            }
          });
          latch.state(2);
        }
        else if (stanza instanceof Message && "groupchat".equals(stanza.getAttribute("type")) && latch.state() == 2) {
          if ("Room is now unlocked".equals(stanza.getWrappedElement().getFirstChild("body").getValue())) {
            System.out.println("Room created & unlocked");
            latch.state(4);
          }
        }
      } catch (JaxmppException e) {
        throw new RuntimeException(e);
      }
    });
    jaxmpp.send(presence);
    latch.state(4, 1);
    rooms.add(room);
    activeRoom = room;
    return room;
  }

  public void topic(String topic) {
    if (activeRoom == null)
      throw new IllegalStateException();
    /*
    <message from="ccc@muc.localhost/solar" type="groupchat" to="solar@localhost/solar-osx" id="aaf6a">
<subject>Рфрф</subject>
<delay xmlns="urn:xmpp:delay" stamp="2015-09-24T14:15:16Z"/>
</message>
     */
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
    final StateLatch latch = new StateLatch();
    client.start();
    client.online();
    client.startRoom();
    client.topic("Hello world");
    latch.state(8, 1);
    client.stop();
  }
}
