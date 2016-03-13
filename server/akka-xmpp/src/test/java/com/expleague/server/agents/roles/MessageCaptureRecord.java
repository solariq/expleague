package com.expleague.server.agents.roles;

import akka.actor.ActorRef;

/**
 * @author vpdelta
 */
public class MessageCaptureRecord {
  private final ActorRef from;
  private final ActorRef to;
  private final Object message;

  public MessageCaptureRecord(final ActorRef from, final ActorRef to, final Object message) {
    this.from = from;
    this.to = to;
    this.message = message;
  }

  public ActorRef getFrom() {
    return from;
  }

  public ActorRef getTo() {
    return to;
  }

  public Object getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return "{" +
      "from=" + from +
      ", to=" + to +
      ", message=" + message +
      '}';
  }
}
