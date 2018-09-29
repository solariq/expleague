package com.expleague.util.akka;

import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.actor.UntypedActor;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.PersistentActor;
import akka.persistence.UntypedPersistentActor;
import scala.compat.java8.functionConverterImpls.FromJavaConsumer;

import java.util.function.Consumer;


/**
 * @author vpdelta
 */
public abstract class PersistentActorAdapter extends ActorAdapter<AbstractActor> {
  public <T> void persist(final T event, final Consumer<? super T> handler) {
    if (actor instanceof AbstractPersistentActor) {
      ((AbstractPersistentActor) actor).persist(event, handler::accept);
    }
    else {
      handler.accept(event);
    }
  }

  @Override
  protected void stash() {
    if (actor instanceof AbstractPersistentActor) {
      ((AbstractPersistentActor) actor).stash();
    }
  }

  @Override
  protected void unstashAll() {
    if (actor instanceof AbstractPersistentActor) {
      ((AbstractPersistentActor) actor).unstashAll();
    }
  }

  public void deleteMessages() {
    if (actor instanceof AbstractPersistentActor) {
      final AbstractPersistentActor actor = (AbstractPersistentActor) this.actor;
      actor.snapshotSequenceNr();
      actor.deleteMessages(actor.lastSequenceNr());
    }
  }

  public abstract String persistenceId();

  public void onReceiveRecover(final Object msg) throws Exception {
  }
}
