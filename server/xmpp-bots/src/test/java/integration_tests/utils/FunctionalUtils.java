package integration_tests.utils;

import java.util.function.Predicate;

/**
 * User: Artem
 * Date: 07.03.2017
 * Time: 16:57
 */
public class FunctionalUtils {
  @FunctionalInterface
  public interface ThrowablePredicate<T> {
    boolean test(T t) throws Exception;
  }

  public static <T> Predicate<T> throwablePredicate(ThrowablePredicate<T> predicate) {
    return t -> {
      try {
        return predicate.test(t);
      } catch (Exception exception) {
        throwAsUnchecked(exception);
        return false;
      }
    };
  }

  private static <E extends Throwable> void throwAsUnchecked(Exception e) throws E {
    //noinspection unchecked,ConstantConditions
    throw (E) e;
  }
}
