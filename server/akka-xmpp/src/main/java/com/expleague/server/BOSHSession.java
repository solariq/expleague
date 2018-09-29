package com.expleague.server;

import akka.actor.*;
import akka.io.Tcp;
import akka.io.TcpMessage;
import akka.util.Timeout;
import com.expleague.util.akka.*;
import com.spbsu.commons.func.Action;
import com.expleague.server.xmpp.XMPPClientConnection;
import com.expleague.server.xmpp.phase.AuthorizationPhase;
import com.expleague.server.xmpp.phase.ConnectedPhase;
import com.expleague.xmpp.BoshBody;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.control.Close;
import com.expleague.xmpp.control.Open;
import scala.concurrent.duration.Duration;
import scala.util.Failure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 24.12.15
 * Time: 19:56
 */
public class BOSHSession extends ActorAdapter<AbstractActor> {
  private static final Logger log = Logger.getLogger(BOSHSession.class.getName());
  private ActorRef businesLogic;
  private String id;
  private ActorRef connection;
  private Queue<Item> outgoing = new ArrayDeque<>();
  private Cancellable timeout;
  private boolean connected = false;

  @Override
  protected void init() {
    invoke(XMPPClientConnection.ConnectionState.AUTHORIZATION);
  }

  @ActorMethod
  public void invoke(BoshBody body) {
    if (this.connection != null)
      this.connection.tell(new ArrayList(), self());
    this.connection = sender();
    for (final Item item : body.items()) {
      businesLogic.tell(item, self());
    }
    if (!body.terminate()) {
      if (!outgoing.isEmpty())
        invoke(Timeout.zero());

      // schedule answer in half an hour in case of no messages received
      if (timeout != null)
        timeout.cancel();
      timeout = AkkaTools.scheduleTimeout(context(), Duration.create(30, TimeUnit.SECONDS), self());
    }
    else {
      invoke(XMPPClientConnection.ConnectionState.CLOSED);
      connection.tell(Collections.emptyList(), self());
      connection = null;
    }
  }

  @ActorMethod
  public void invoke(Item item) {
    if (item instanceof BoshBody || item instanceof Open)
      return;
    if (timeout != null)
      timeout.cancel();
    outgoing.add(item);
    if (connected) // batching
      timeout = context().system().scheduler().scheduleOnce(Duration.create(50, TimeUnit.MILLISECONDS), self(), Timeout.zero(), context().dispatcher(), self());
    else
      invoke(Timeout.zero());
  }

  @SuppressWarnings("UnusedParameters")
  @ActorMethod
  public void invoke(TcpMessage cmd) {}

  @ActorMethod
  public void invoke(XMPPClientConnection.ConnectionState state) {
    switch (state) {
      case AUTHORIZATION:
        final Object[] args = new Object[]{self(), (Action<String>) id -> BOSHSession.this.id = id};
        businesLogic = context().actorOf(props(AuthorizationPhase.class, args));
        break;
      case CONNECTED:
        connected = true;
        invoke(Timeout.zero());
        businesLogic = context().actorOf(props(ConnectedPhase.class, self(), id));
        break;
      case CLOSED:
        businesLogic.tell(new Close(), self());
        context().stop(self());
        return;
    }
    businesLogic.tell(new Open(), self());
  }

  @ActorMethod
  public void invoke(Tcp.SuspendReading$ ignore) {}

  @ActorMethod
  public void invoke(Status.Failure failure) {
    //noinspection ThrowableResultOfMethodCallIgnored
    if (failure.cause() != null)
      log.log(Level.WARNING, "", failure.cause());
    else
      log.log(Level.WARNING, failure.toString());
  }

  @ActorMethod
  public void invoke(Failure failure) {
    //noinspection ThrowableResultOfMethodCallIgnored
    if (failure.exception() != null)
      log.log(Level.WARNING, "", failure.exception());
    else
      log.log(Level.WARNING, failure.toString());
  }

  @SuppressWarnings("UnusedParameters")
  @ActorMethod
  public void invoke(Timeout to) {
    if (connection != null) {
      connection.tell(new ArrayList<>(outgoing), self());
      outgoing.clear();
    }
    connection = null;
  }
}
