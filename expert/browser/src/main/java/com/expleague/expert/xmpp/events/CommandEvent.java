package com.expleague.expert.xmpp.events;

import com.expleague.expert.xmpp.ExpertEvent;
import com.expleague.model.Operations;
import com.expleague.xmpp.stanza.Message;

/**
 * Experts League
 * Created by solar on 10/02/16.
 */
public class CommandEvent extends ExpertEvent {
  private final Operations.Command command;
  public CommandEvent(Message source) {
    super(source);
    command = source.get(Operations.Command.class);
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
