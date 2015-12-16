package com.tbts.util.akka;

import akka.actor.UntypedActor;
import com.spbsu.commons.system.RuntimeUtils;

/**
 * User: solar
 * Date: 03.12.15
 * Time: 15:57
 */
public abstract class UntypedActorAdapter extends UntypedActor {
  private RuntimeUtils.InvokeDispatcher dispatcher;

  public UntypedActorAdapter() {
    dispatcher = new RuntimeUtils.InvokeDispatcher(getClass(), this::unhandled);
  }

  @Override
  public final void onReceive(Object message) throws Exception {
    dispatcher.invoke(this, message);
  }
}
