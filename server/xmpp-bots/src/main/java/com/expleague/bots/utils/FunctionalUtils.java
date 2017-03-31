package com.expleague.bots.utils;

import java.util.function.Consumer;
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

  @FunctionalInterface
  public interface ThrowableConsumer<T> {
    void accept(T t) throws Exception;
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

  public static <T> Consumer<T> throwableConsumer(ThrowableConsumer<T> consumer) {
    return t -> {
      try {
        consumer.accept(t);
      } catch (Exception exception) {
        throwAsUnchecked(exception);
      }
    };
  }

  private static <E extends Throwable> void throwAsUnchecked(Exception e) throws E {
    //noinspection unchecked,ConstantConditions
    throw (E) e;
  }
}
