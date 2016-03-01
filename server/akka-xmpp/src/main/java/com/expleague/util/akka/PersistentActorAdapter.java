package com.expleague.util.akka;

import akka.persistence.UntypedPersistentActor;
import scala.compat.java8.functionConverterImpls.FromJavaConsumer;

import java.util.function.Consumer;


/**
 * @author vpdelta
 */
public abstract class PersistentActorAdapter extends ActorAdapter<UntypedPersistentActor> {
  public void persist(final Object event, final Consumer<Object> handler) {
    actor.persist(event, new FromJavaConsumer(handler));
  }

  public abstract String persistenceId();

  public void onReceiveRecover(final Object msg) throws Exception {
  }
}
