package com.expleague.expert.xmpp;

import com.expleague.expert.profile.ProfileManager;
import com.expleague.expert.profile.UserProfile;
import com.expleague.expert.xmpp.events.CheckEvent;
import com.expleague.expert.xmpp.events.CommandEvent;
import com.expleague.expert.xmpp.events.MessageEvent;
import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Stanza;

/**
 * Experts League
 * Created by solar on 10/02/16.
 */
public class ExpLeagueExpert extends WeakListenerHolderImpl<ExpertEvent> implements StanzaListener {

  public JID jid() {
    final String id = ProfileManager.instance().active().get(UserProfile.Key.EXP_LEAGUE_ID);
    return JID.parse(id);
  }
  @Override
  public void processPacket(Stanza packet) throws SmackException.NotConnectedException {
    if (packet instanceof IQ)
      return;
    final Item item = Item.create(packet.toXML());
    if (item instanceof Message) {
      final ExpertEvent event;
      final Message message = (Message) item;
      if (message.has(Offer.class) || message.has(Operations.Command.class)) {
        if (message.has(Operations.Command.class))
          event = new CommandEvent(message);
        else {
          event = new CheckEvent(message);
          ExpLeagueConnection.instance().send(new Message(jid(), message.from(), message.get(Offer.class), new Operations.Ok()));
        }
      }
      else event = new MessageEvent(message);
      invoke(event);
    }
  }
}
