package com.expleague.expert.xmpp;

import com.expleague.xmpp.Item;

/**
 * Experts League
 * Created by solar on 10/02/16.
 */
public class ExpertEvent {
  private final Item source;

  public ExpertEvent(Item source) {
    this.source = source;
  }

  public Item source() {
    return source;
  }
}
