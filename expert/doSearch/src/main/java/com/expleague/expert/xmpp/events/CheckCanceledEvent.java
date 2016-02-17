package com.expleague.expert.xmpp.events;

import com.expleague.expert.xmpp.ExpertEvent;
import com.expleague.xmpp.stanza.Stanza;

/**
 * Experts League
 * Created by solar on 10/02/16.
 */
public class CheckCanceledEvent extends ExpertEvent {
  public CheckCanceledEvent(Stanza source) {
    super(source);
  }
}
