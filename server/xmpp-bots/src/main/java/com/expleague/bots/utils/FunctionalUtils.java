package com.expleague.bots.utils;

import java.util.function.Supplier;

/**
 * User: Artem
 * Date: 07.03.2017
 * Time: 16:57
 */
public class FunctionalUtils {
  @FunctionalInterface
  public interface ThrowableSupplier<T> {
    T get() throws Exception;
  }

  public static <T> Supplier<T> throwableSupplier(ThrowableSupplier<T> supplier) {
    return () -> {
      try {
        return supplier.get();
      } catch (Exception exception) {
        throwAsUnchecked(exception);
        return null;
      }
    };
  }

  private static <E extends Throwable> void throwAsUnchecked(Exception e) throws E {
    //noinspection unchecked,ConstantConditions
    throw (E) e;
  }
}
