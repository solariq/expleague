package com.expleague.bots;

import com.spbsu.commons.util.sync.StateLatch;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.Connector;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;

/**
 * User: Artem
 * Date: 14.02.2017
 * Time: 16:27
 */
public class AdminBot extends Bot {
  private static final long DEFAULT_TIMEOUT_IN_NANOS = 60L * 1000L * 1000L * 1000L;

  public AdminBot(final BareJID jid, final String passwd) throws JaxmppException {
    super(jid, passwd, "expert", "/admin/expert");
  }

  public Message receiveMessage() {
    return receiveMessage(DEFAULT_TIMEOUT_IN_NANOS);
  }

  public Message receiveMessage(long timeoutInNanos) {
    final StateLatch latch = new StateLatch();
    final Message[] message = new Message[1];
    final Connector.StanzaReceivedHandler handler = (sessionObject, stanza) -> {
      if (stanza instanceof Message) {
        message[0] = (Message) stanza;
        latch.advance();
      }
    };
    jaxmpp.getEventBus().addHandler(Connector.StanzaReceivedHandler.StanzaReceivedEvent.class, handler);
    latch.state(2, 1, timeoutInNanos);
    jaxmpp.getEventBus().remove(Connector.StanzaReceivedHandler.StanzaReceivedEvent.class, handler);

    if (message[0] == null) {
      throw new RuntimeException("timeout");
    }
    return message[0];
  }

  public static void main(final String[] args) throws JaxmppException, InterruptedException {
    final AdminBot admin = new AdminBot(BareJID.bareJIDInstance("expert-bot-1", "localhost"), "poassord");
    admin.start();
    admin.online();

    final Message message = admin.receiveMessage();
    System.out.println(message.getAsString());

    admin.stop();
  }
}
