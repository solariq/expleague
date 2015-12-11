package com.tbts.util.akka;

import akka.actor.UntypedActor;
import com.spbsu.commons.util.MultiMap;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * User: solar
 * Date: 03.12.15
 * Time: 15:57
 */
public abstract class UntypedActorAdapter extends UntypedActor {
  public final MultiMap<Class<?>, Method> methodMap = new MultiMap<>();

  public UntypedActorAdapter() {
    Arrays.asList(getClass().getMethods()).stream()
        .filter(method -> "invoke".equals(method.getName()) && method.getParameterCount() == 1 && method.getReturnType() == void.class)
        .forEach(method -> methodMap.put(method.getParameterTypes()[0], method));
  }

  @Override
  public final void onReceive(Object message) throws Exception {
    Collection<Method> methods = methodMap.get(message.getClass());
    if (methods == MultiMap.EMPTY) {
      methods = new ArrayList<>();
      for (final Class<?> aClass : methodMap.keySet()) {
        if (aClass.isAssignableFrom(message.getClass())) {
          methods.addAll(methodMap.get(aClass));
        }
      }
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
