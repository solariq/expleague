package com.tbts.util.akka;

import akka.actor.UntypedActor;
import com.spbsu.commons.util.MultiMap;

import java.lang.reflect.Method;
import java.util.*;

/**
 * User: solar
 * Date: 03.12.15
 * Time: 15:57
 */
public abstract class UntypedActorAdapter extends UntypedActor {
  public final Map<Class<?>, Method> typesMap = new HashMap<>();
  public final MultiMap<Class<?>, Method> cache = new MultiMap<>();

  public UntypedActorAdapter() {
    Arrays.asList(getClass().getMethods()).stream()
        .filter(method -> "invoke".equals(method.getName()) && method.getParameterCount() == 1 && method.getReturnType() == void.class)
        .forEach(method -> typesMap.put(method.getParameterTypes()[0], method));
  }

  @Override
  public final void onReceive(Object message) throws Exception {
    Collection<Method> methods = cache.get(message.getClass());
    if (methods == MultiMap.EMPTY) {
      methods = new ArrayList<>();
      for (final Class<?> aClass : typesMap.keySet()) {
        if (aClass.isAssignableFrom(message.getClass())) {
          methods.add(typesMap.get(aClass));
        }
      }
      cache.putAll(message.getClass(), methods);
    }
    if (!methods.isEmpty()) {
      for (final Method method : methods) {
        method.invoke(this, message);
      }
    }
    else {
      System.out.println("Unhandeled!: " + message.toString());
      unhandled(message);
    }
  }
}
