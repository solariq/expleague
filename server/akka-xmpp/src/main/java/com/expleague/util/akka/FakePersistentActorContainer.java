package com.expleague.util.akka;

import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.persistence.RecoveryCompleted;

/**
 * User: Artem
 * Date: 17.02.2017
 * Time: 17:37
 */
public class FakePersistentActorContainer extends UntypedActor {
  private final ActorInvokeDispatcher<PersistentActorAdapter> dispatcher;

  public static Props props(final Class<? extends ActorAdapter> adapter, final Object... args) {
    return Props.create(FakePersistentActorContainer.class, new Object[]{new AdapterProps[]{AdapterProps.create(adapter, args)}});
  }

  public static Props props(
      final AdapterProps adapterProps,
      final AdapterProps overrideProps
  ) {
    return Props.create(FakePersistentActorContainer.class, new Object[]{new AdapterProps[]{adapterProps, overrideProps}});
  }

  public FakePersistentActorContainer(final AdapterProps[] props) {
    dispatcher = new ActorInvokeDispatcher<>(this, props, this::unhandled);
  }

  @Override
  public void preStart() throws Exception {
    super.preStart();
    getAdapterInstance().preStart();
    getAdapterInstance().onReceiveRecover(RecoveryCompleted.getInstance());
  }

  @Override
  public void postStop() throws Exception {
    getAdapterInstance().postStop();
    super.postStop();
  }

  @Override
  public void onReceive(final Object message) throws Exception {
    if (ActorFailureChecker.checkIfFailure(getAdapterInstance().getClass(), self().path().name(), message)) {
      return;
    }

    MessageCapture.instance().capture(sender(), self(), message);

    dispatcher.invoke(message);
  }

  private PersistentActorAdapter getAdapterInstance() {
    return dispatcher.getDispatchSequence().get(0).getInstance();
  }
}