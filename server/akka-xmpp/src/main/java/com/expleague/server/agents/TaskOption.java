package com.expleague.server.agents;

import akka.actor.ActorRef;
import com.expleague.model.Offer;
import com.expleague.model.Operations;

/**
 * @author vpdelta
 */
public class TaskOption {
  private final ActorRef broker;
  private final Offer offer;
  private final Operations.Command sourceCommand;

  public TaskOption(final ActorRef broker, final Offer offer, final Operations.Command sourceCommand) {
    this.broker = broker;
    this.offer = offer;
    this.sourceCommand = sourceCommand;
  }

  public ActorRef getBroker() {
    return broker;
  }

  public Offer getOffer() {
    return offer;
  }

  public Operations.Command getSourceCommand() {
    return sourceCommand;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final TaskOption taskOption = (TaskOption) o;

    if (!broker.equals(taskOption.broker)) return false;
    return offer.equals(taskOption.offer);

  }

  @Override
  public int hashCode() {
    int result = broker.hashCode();
    result = 31 * result + offer.hashCode();
    return result;
  }
}
