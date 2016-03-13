package com.expleague.xmpp;

import java.util.List;

/**
 * Experts League
 * Created by solar on 13/03/16.
 */
public interface AnyHolder {
  List<? super Item> any();

  default  <T> T get(Class<T> clazz) {
    //noinspection unchecked
    return (T)this.any().stream().filter(x -> clazz.isAssignableFrom(x.getClass())).findAny().orElse(null);
  }

  default <S extends AnyHolder, T extends Item> S append(T o) {
    any().add(o);
    //noinspection unchecked
    return (S)this;
  }


  default boolean has(Class<?> clazz) {
    return any().stream().filter(x -> clazz.isAssignableFrom(x.getClass())).findAny().isPresent();
  }
}
