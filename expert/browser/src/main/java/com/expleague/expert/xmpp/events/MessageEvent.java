package com.expleague.expert.xmpp.events;

import com.expleague.expert.xmpp.ExpertEvent;
import com.expleague.xmpp.stanza.Message;

/**
 * Experts League
 * Created by solar on 10/02/16.
 */
public class MessageEvent extends ExpertEvent {
  public MessageEvent(Message source) {
    super(source);
  }

  public void visitParts(PartsVisitor visitor) {
    visitor.accept(source().body());
  }

  @Override
  public Message source() {
    return (Message)super.source();
  }

  public static class PartsVisitor {
    public void accept(String text) { }
  }
}
