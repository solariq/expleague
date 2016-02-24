package com.expleague.expert.xmpp.events;

import com.expleague.expert.xmpp.ExpertEvent;
import com.expleague.expert.xmpp.ExpertTask;
import com.expleague.model.Image;
import com.expleague.xmpp.stanza.Message;

/**
 * Experts League
 * Created by solar on 10/02/16.
 */
public class ChatMessageEvent extends ExpertTaskEvent {
  private final boolean incoming;

  public ChatMessageEvent(ExpertTask task, Message source, boolean incoming) {
    super(source, task);
    this.incoming = incoming;
  }

  public void visitParts(PartsVisitor visitor) {
    visitor.accept(source().body());
    if (source().has(Image.class)) {
      visitor.accept(source().get(Image.class));
    }
  }

  @Override
  public Message source() {
    return (Message)super.source();
  }

  public static class PartsVisitor {
    public void accept(String text) { }
    public void accept(Image text) { }
  }
}
