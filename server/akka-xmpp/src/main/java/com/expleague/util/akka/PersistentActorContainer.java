package com.expleague.util.akka;

import akka.actor.Props;
import akka.persistence.UntypedPersistentActor;

/**
 * @author vpdelta
 */
public class PersistentActorContainer extends UntypedPersistentActor {
  private final ActorInvokeDispatcher<PersistentActorAdapter> dispatcher;

  public static Props props(final Class<? extends ActorAdapter> adapter, final Object... args) {
    return Props.create(PersistentActorContainer.class, new Object[] {new AdapterProps[] {AdapterProps.create(adapter, args)}});
  }

  public static Props props(
    final AdapterProps adapterProps,
    final AdapterProps overrideProps
  ) {
    return Props.create(PersistentActorContainer.class, new Object[] {new AdapterProps[] {adapterProps, overrideProps}});
  }

  public PersistentActorContainer(final AdapterProps[] props) {
    dispatcher = new ActorInvokeDispatcher<>(this, props, this::unhandled);
  }

  @Override
  public void onReceiveRecover(final Object msg) throws Exception {
    getAdapterInstance().onReceiveRecover(msg);
  }

  @Override
  public void onReceiveCommand(final Object message) throws Exception {
    if (ActorFailureChecker.checkIfFailure(getAdapterInstance().getClass(), self().path().name(), message)) {
      return;
    }

    dispatcher.invoke(message);
  }

  @Override
  public String persistenceId() {
    return getAdapterInstance().persistenceId();
  }

  private PersistentActorAdapter getAdapterInstance() {
    return dispatcher.getDispatchSequence().get(0).getInstance();
  }
}
