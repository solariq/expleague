package com.tbts.server.xmpp.phase;

import akka.stream.stage.Context;
import akka.stream.stage.PushPullStage;
import akka.stream.stage.SyncDirective;
import com.spbsu.commons.util.MultiMap;
import com.tbts.xmpp.Item;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 08.12.15
 * Time: 17:15
 */
public class XMPPPhase extends PushPullStage<Item, Item> {
  private static final Logger log = Logger.getLogger(XMPPPhase.class.getName());
  private final MultiMap<Class<?>, Method> methodMap = new MultiMap<>();
  private boolean stopped = false;

  protected XMPPPhase() {
    Arrays.asList(getClass().getMethods()).stream()
        .filter(method -> "invoke".equals(method.getName()) && method.getParameterCount() == 1 && method.getReturnType() == void.class)
        .forEach(method -> methodMap.put(method.getParameterTypes()[0], method));
  }

  protected void unhandled(Item msg) {
    log.log(Level.WARNING, "Unexpected xmpp item: " + msg);
  }
  protected void answer(Item item) {
    out.add(item);
  }
  protected void stop() {
    stopped = true;
  }

  private final Queue<Item> out = new ArrayDeque<>();
  @Override
  public SyncDirective onPush(Item message, Context<Item> itemContext) {
    invoke(message);
    return onPull(itemContext);
  }

  private void invoke(Object message) {
    Collection<Method> methods = methodMap.get(message.getClass());
    if (methods == MultiMap.EMPTY) {
      methods = new ArrayList<>();
      for (final Class<?> aClass : methodMap.keySet()) {
        if (aClass.isAssignableFrom(message.getClass())) {
          methods.addAll(methodMap.get(aClass));
        }
      }
      methodMap.putAll(message.getClass(), methods);
    }
    if (!methods.isEmpty()) {
      for (final Method method : methods) {
        try {
          method.invoke(this, message);
        }
        catch (IllegalAccessException | InvocationTargetException e) {
          throw new RuntimeException(e);
        }
      }
    }
    else if (message instanceof Item)
      unhandled((Item) message);
  }

  @Override
  public SyncDirective onPull(Context<Item> itemContext) {
    return !out.isEmpty() ? itemContext.push(out.poll()) : !stopped ? itemContext.pull() : itemContext.finish();
  }
}
