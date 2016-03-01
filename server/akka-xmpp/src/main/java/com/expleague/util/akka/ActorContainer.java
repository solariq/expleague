package com.expleague.util.akka;

import akka.actor.Props;
import akka.actor.UntypedActor;

/**
 * @author vpdelta
 */
public class ActorContainer extends UntypedActor {
  private final ActorInvokeDispatcher dispatcher;

  public static Props props(final Class<? extends ActorAdapter> adapter, final Object... args) {
    return Props.create(ActorContainer.class, new Class[] {adapter}, args);
  }

  public static Props props(
    final Class<? extends ActorAdapter> adapter,
    final Class<? extends ActorAdapter> override,
    final Object... args
  ) {
    return Props.create(ActorContainer.class, new Class[] {adapter, override}, args);
  }

  public ActorContainer(Class[] classes, final Object[] args) {
    dispatcher = new ActorInvokeDispatcher(this, classes, args, this::unhandled);
  }

  @Override
  public void onReceive(final Object message) throws Exception {
    dispatcher.invoke(message);
  }
}
