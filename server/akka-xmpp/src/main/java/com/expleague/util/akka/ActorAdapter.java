package com.expleague.util.akka;

import akka.actor.Actor;
import akka.actor.ActorContext;
import akka.actor.ActorRef;

/**
 * @author vpdelta
 */
public abstract class ActorAdapter<A extends Actor> {
  protected A actor;

  public ActorAdapter() {
    // to make it possible to create adapter outside of actor system (in tests)
  }

  public final void injectActor(final A actor) {
    this.actor = actor;
    init();
  }

  protected void init() {
  }

  // todo: in future we can have complete delegation here
  public ActorRef self() {
    return getActor().self();
  }

  protected A getActor() {
    if (actor == null) {
      throw new IllegalStateException("Actor is not injected");
    }
    return actor;
  }

  public ActorRef sender() {
    return getActor().sender();
  }

  public ActorContext context() {
    return getActor().context();
  }

  public void unhandled(Object message) {
    getActor().unhandled(message);
  }
}
