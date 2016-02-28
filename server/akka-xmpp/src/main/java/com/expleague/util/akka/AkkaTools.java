package com.expleague.util.akka;

import akka.actor.*;
import akka.pattern.AskableActorRef;
import akka.pattern.AskableActorSelection;
import akka.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 21.12.15
 * Time: 14:48
 */
public class AkkaTools {
  private static final Logger log = Logger.getLogger(AkkaTools.class.getName());
  public static final FiniteDuration AKKA_OPERATION_TIMEOUT = FiniteDuration.create(1, TimeUnit.MINUTES);
  public static final FiniteDuration AKKA_CREATE_TIMEOUT = FiniteDuration.create(30, TimeUnit.SECONDS);

  public static ActorRef getOrCreate(String path, ActorRefFactory context, BiFunction<String, ActorRefFactory, ActorRef> factory) {
    final ActorSelection selection = context.actorSelection(path);
    try {
      return Await.result(selection.resolveOne(AkkaTools.AKKA_CREATE_TIMEOUT), Duration.Inf());
    }
    catch (ActorNotFound anf) {
      return factory.apply(path, context);
    }
    catch (Exception e) {
      log.log(Level.SEVERE, "Error during akka actor creation", e);
      throw new RuntimeException(e);
    }
  }

  public static ActorRef getOrCreate(String path, ActorRefFactory context, Supplier<Props> factory) {
    final ActorSelection selection = context.actorSelection(path);
    try {
      return Await.result(selection.resolveOne(AkkaTools.AKKA_CREATE_TIMEOUT), Duration.Inf());
    }
    catch (ActorNotFound anf) {
      return context.actorOf(factory.get(), path);
    }
    catch (Exception e) {
      log.log(Level.SEVERE, "Error during akka actor creation", e);
      throw new RuntimeException(e);
    }
  }

  public static <T, A> T ask(ActorRef ref, A arg) {
    return ask(ref, arg, Timeout.apply(AkkaTools.AKKA_OPERATION_TIMEOUT));
  }

  public static <T, A> T ask(ActorRef ref, A arg, Timeout to) {
    final AskableActorRef ask = new AskableActorRef(ref);
    //noinspection unchecked
    final Future<T> future = (Future<T>)ask.ask(arg, to);
    try {
      return Await.result(future, Duration.Inf());
    }
    catch (Exception e) {
      log.log(Level.WARNING, "Exception during synchronous ask", e);
      return null;
    }
  }

  public static <T, A> T ask(ActorSelection ref, A arg) {
    return ask(ref, arg, Timeout.apply(AkkaTools.AKKA_OPERATION_TIMEOUT));
  }

  public static <T, A> T ask(ActorSelection ref, A arg, Timeout apply) {
    final AskableActorSelection ask = new AskableActorSelection(ref);
    //noinspection unchecked
    final Future<T> future = (Future<T>)ask.ask(arg, apply);
    try {
      return Await.result(future, Duration.Inf());
    }
    catch (Exception e) {
      log.log(Level.WARNING, "Exception during synchronous ask", e);
      return null;
    }
  }

  public static <T, A> T askOrThrow(ActorSelection ref, A arg, Timeout apply) {
    final AskableActorSelection ask = new AskableActorSelection(ref);
    //noinspection unchecked
    final Future<T> future = (Future<T>)ask.ask(arg, apply);
    try {
      return Await.result(future, Duration.Inf());
    }
    catch (Exception e) {
      throw new RuntimeException("Exception during synchronous ask", e);
    }
  }

  public static Cancellable scheduleTimeout(ActorContext context, FiniteDuration timeout, ActorRef to) {
    return context.system().scheduler().scheduleOnce(timeout, () -> {
      to.tell(Timeout.apply(timeout), to);
    }, context.dispatcher());
  }
}
