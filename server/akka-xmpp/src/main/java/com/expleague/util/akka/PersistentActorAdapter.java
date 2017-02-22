package com.expleague.util.akka;

import akka.actor.Actor;
import akka.persistence.PersistentActor;
import akka.persistence.UntypedPersistentActor;
import scala.compat.java8.functionConverterImpls.FromJavaConsumer;

import java.util.function.Consumer;


/**
 * @author vpdelta
 */
public abstract class PersistentActorAdapter extends ActorAdapter<Actor> {
  public <T> void persist(final T event, final Consumer<? super T> handler) {
    if (actor instanceof UntypedPersistentActor) {
      ((UntypedPersistentActor) actor).persist(event, new FromJavaConsumer<>(handler));
    }
    else {
      handler.accept(event);
    }
  }

  public void deleteMessages() {
    if (actor instanceof PersistentActor) {
      ((PersistentActor) actor).deleteMessages(-1);
    }
  }

  public abstract String persistenceId();

  public void onReceiveRecover(final Object msg) throws Exception {
  }
}
