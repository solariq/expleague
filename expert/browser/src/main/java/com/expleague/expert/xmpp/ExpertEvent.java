package com.expleague.expert.xmpp;

import org.jivesoftware.smack.packet.Stanza;

/**
 * Experts League
 * Created by solar on 10/02/16.
 */
public class ExpertEvent {
  private final Stanza source;

  public ExpertEvent(Stanza source) {
    this.source = source;
  }

  public Stanza source() {
    return source;
  }
}
