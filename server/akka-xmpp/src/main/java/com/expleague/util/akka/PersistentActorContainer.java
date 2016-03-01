package com.expleague.util.akka;

import akka.actor.Props;
import akka.persistence.UntypedPersistentActor;

/**
 * @author vpdelta
 */
public class PersistentActorContainer extends UntypedPersistentActor {
  private final ActorInvokeDispatcher<PersistentActorAdapter> dispatcher;
  private final ActorInvokeDispatcher<PersistentActorAdapter> recoverDispatcher;

  public static Props props(final Class<? extends PersistentActorAdapter> adapter, final Object... args) {
    return Props.create(PersistentActorContainer.class, new Class[] {adapter}, args);
  }

  public static Props props(
    final Class<? extends PersistentActorAdapter> adapter,
    final Class<? extends PersistentActorAdapter> override,
    final Object... args
  ) {
    return Props.create(PersistentActorContainer.class, new Class[] {adapter, override}, args);
  }

  public PersistentActorContainer(Class[] classes, final Object[] args) {
    dispatcher = new ActorInvokeDispatcher<>(this, classes, args, this::unhandled);
    recoverDispatcher = new ActorInvokeDispatcher<>(this, classes, args, this::unhandled, ActorRecover.class);
  }

  @Override
  public void onReceiveRecover(final Object msg) throws Exception {
    recoverDispatcher.invoke(msg);
  }

  @Override
  public void onReceiveCommand(final Object msg) throws Exception {
    dispatcher.invoke(msg);
  }

  @Override
  public String persistenceId() {
    return dispatcher.getDispatchSequence().get(0).getInstance().persistenceId();
  }
}
