package com.expleague.util.akka;

import akka.actor.Actor;
import akka.actor.UntypedActor;
import akka.persistence.PersistentActor;
import akka.persistence.UntypedPersistentActor;
import scala.compat.java8.functionConverterImpls.FromJavaConsumer;

import java.util.function.Consumer;


/**
 * @author vpdelta
 */
public abstract class PersistentActorAdapter extends ActorAdapter<UntypedActor> {
  public <T> void persist(final T event, final Consumer<? super T> handler) {
    if (actor instanceof UntypedPersistentActor) {
      ((UntypedPersistentActor) actor).persist(event, new FromJavaConsumer<>(handler));
    }
    else {
      handler.accept(event);
    }
  }

  @Override
  protected void stash() {
    if (actor instanceof PersistentActorContainer) {
      ((PersistentActorContainer) actor).stash();
    }
  }

  @Override
  protected void unstashAll() {
    if (actor instanceof PersistentActorContainer) {
      ((PersistentActorContainer) actor).unstashAll();
    }
  }

  public void deleteMessages() {
    if (actor instanceof PersistentActorContainer) {
      final PersistentActorContainer actor = (PersistentActorContainer) this.actor;
      actor.snapshotSequenceNr();
      actor.deleteMessages(actor.lastSequenceNr());
    }
  }

  public abstract String persistenceId();

  public void onReceiveRecover(final Object msg) throws Exception {
  }
}
