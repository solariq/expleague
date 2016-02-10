package com.tbts.util.akka;

import akka.actor.UntypedActor;
import com.spbsu.commons.system.RuntimeUtils;
import scala.util.Failure;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 03.12.15
 * Time: 15:57
 */
public abstract class UntypedActorAdapter extends UntypedActor {
  private static final Logger log = Logger.getLogger(UntypedActorAdapter.class.getName());
  private RuntimeUtils.InvokeDispatcher dispatcher;

  public UntypedActorAdapter() {
    dispatcher = new RuntimeUtils.InvokeDispatcher(getClass(), this::unhandled);
  }

  @Override
  public final void onReceive(Object message) throws Exception {
    if (message instanceof Failure) {
      final Failure failure = (Failure) message;
      //noinspection ThrowableResultOfMethodCallIgnored
      if (failure.exception() != null)
        log.log(Level.WARNING, "Failure in" + getClass().getName() + "", failure.exception());
      else
        log.log(Level.WARNING, failure.toString());
      return;
    }

    dispatcher.invoke(this, message);
  }
}
