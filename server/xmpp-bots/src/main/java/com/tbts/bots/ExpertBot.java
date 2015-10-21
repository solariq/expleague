package com.tbts.bots;

import com.spbsu.commons.util.sync.StateLatch;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;

/**
 * User: solar
 * Date: 11.10.15
 * Time: 21:10
 */
public class ExpertBot extends Bot {

  private BareJID roomJID;

  public ExpertBot(final BareJID jid, final String passwd) throws JaxmppException {
    super(jid, passwd, "expert");
    jaxmpp.getEventBus().addHandler(MucModule.InvitationReceivedHandler.InvitationReceivedEvent.class, (sessionObject, invitation, id, bareJID) -> {
      onInvite(invitation);
    });
  }

  public void onInvite(MucModule.Invitation invitation) {
  }


  public static void main(final String[] args) throws JaxmppException, InterruptedException {
    final StateLatch latch = new StateLatch();
    final ExpertBot expert = new ExpertBot(BareJID.bareJIDInstance("expert-bot-1", "localhost"), "poassord") {
      @Override
      public void onInvite(MucModule.Invitation invitation) {
        accept(invitation);
        latch.advance();
      }
    };
    expert.start();
//    expert.offline();
    expert.online();
    expert.onClose(latch::advance);
    latch.state(2, 1);
    expert.answer("Otvali!");
    Thread.sleep(200);
    expert.offline();
    expert.stop();
  }

  public void offline() {
    try {
      final Presence presence = Presence.create();
      presence.setShow(Presence.Show.xa);
      jaxmpp.send(presence);
    } catch (JaxmppException e) {
      throw new RuntimeException(e);
    }
  }

  public void answer(String text) {
    try {
      final Message msg = Message.create();
      msg.setTo(JID.jidInstance(roomJID));
      msg.setType(StanzaType.groupchat);
      msg.setBody(text);
      jaxmpp.send(msg);
    } catch (JaxmppException e) {
      throw new RuntimeException(e);
    }
  }

  public void accept(MucModule.Invitation invitation) {
    try {
      final Presence presence = Presence.create();
      presence.setShow(Presence.Show.online);
      roomJID = invitation.getRoomJID();
      presence.setTo(JID.jidInstance(roomJID, "expert"));
      jaxmpp.send(presence);
    } catch (JaxmppException e) {
      throw new RuntimeException(e);
    }
  }
}
