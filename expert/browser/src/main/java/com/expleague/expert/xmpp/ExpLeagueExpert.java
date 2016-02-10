package com.expleague.expert.xmpp;

import com.expleague.expert.xmpp.events.MessageEvent;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;

/**
 * Experts League
 * Created by solar on 10/02/16.
 */
public class ExpLeagueExpert extends WeakListenerHolderImpl<ExpertEvent> implements StanzaListener {
  @Override
  public void processPacket(Stanza packet) throws SmackException.NotConnectedException {
    if (packet instanceof Message) {
      final Message message = (Message) packet;
      invoke(new MessageEvent(message));
    }
  }
}
