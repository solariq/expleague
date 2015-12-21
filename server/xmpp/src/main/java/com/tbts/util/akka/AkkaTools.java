package com.tbts.util.akka;

import akka.actor.*;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * User: solar
 * Date: 21.12.15
 * Time: 14:48
 */
public class AkkaTools {
  public static final FiniteDuration AKKA_OPERATION_TIMEOUT = FiniteDuration.create(1, TimeUnit.SECONDS);

  public static ActorRef getOrCreate(String path, ActorRefFactory context, BiFunction<String, ActorRefFactory, ActorRef> factory) {
    final ActorSelection selection = context.actorSelection(path);
    try {
      return Await.result(selection.resolveOne(AkkaTools.AKKA_OPERATION_TIMEOUT), Duration.Inf());
    }
    catch (ActorNotFound anf) {
      return factory.apply(path, context);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static ActorRef getOrCreate(String path, ActorRefFactory context, Supplier<Props> factory) {
    final ActorSelection selection = context.actorSelection(path);
    try {
      return Await.result(selection.resolveOne(AkkaTools.AKKA_OPERATION_TIMEOUT), Duration.Inf());
    }
    catch (ActorNotFound anf) {
      return context.actorOf(factory.get(), path);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
