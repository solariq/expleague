package com.expleague.expert.xmpp.events;

import com.expleague.expert.xmpp.ExpertEvent;
import org.jivesoftware.smack.packet.Message;

/**
 * Experts League
 * Created by solar on 10/02/16.
 */
public class MessageEvent extends ExpertEvent {
  public MessageEvent(Message source) {
    super(source);
  }

  public void visitParts(PartsVisitor visitor) {
    for (Message.Body body : source().getBodies()) {
      visitor.accept(body.getMessage());
    }
  }

  @Override
  public Message source() {
    return (Message)super.source();
  }

  public static class PartsVisitor {
    public void accept(String text) { }
  }
}
