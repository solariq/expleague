package com.expleague.util.akka;

import akka.actor.Actor;
import com.spbsu.commons.func.Action;
import com.spbsu.commons.system.RuntimeUtils;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

/**
 * @author vpdelta
 */
public class ActorInvokeDispatcher<A extends ActorAdapter> {
  private final List<Dispatcher> dispatchSequence = new ArrayList<>();

  public ActorInvokeDispatcher(final Actor actor, Class<? extends A>[] classes, Object[] args, Action<Object> unhandledCallback) {
    this(actor, classes, args, unhandledCallback, ActorMethod.class);
  }

  public ActorInvokeDispatcher(final Actor actor, Class<? extends A>[] classes, Object[] args, Action<Object> unhandledCallback, Class<? extends Annotation> annotation) {
    Action<Object> currentUnhandledCallback = unhandledCallback;
    for (Class<?> clazz : classes) {
      try {
        // todo: quite dirty
        final A instance = (A) clazz.getConstructors()[0].newInstance(args);
        instance.injectActor(actor);
        final Dispatcher dispatcher = new Dispatcher(
          instance,
          new RuntimeUtils.InvokeDispatcher(clazz, currentUnhandledCallback, annotation)
        );
        dispatchSequence.add(dispatcher);
        currentUnhandledCallback = dispatcher;
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  public List<Dispatcher> getDispatchSequence() {
    return dispatchSequence;
  }

  public final void invoke(Object message) {
    dispatchSequence.get(dispatchSequence.size() - 1).invoke(message);
  }

  public class Dispatcher implements Action<Object> {
    private final A instance;
    private final RuntimeUtils.InvokeDispatcher invokeDispatcher;

    public Dispatcher(final A instance, final RuntimeUtils.InvokeDispatcher invokeDispatcher) {
      this.instance = instance;
      this.invokeDispatcher = invokeDispatcher;
    }

    @Override
    public void invoke(final Object o) {
      invokeDispatcher.invoke(instance, o);
    }

    public A getInstance() {
      return instance;
    }
  }
}
