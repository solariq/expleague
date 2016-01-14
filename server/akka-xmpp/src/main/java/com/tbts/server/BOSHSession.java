package com.tbts.server;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.Status;
import akka.io.TcpMessage;
import akka.util.Timeout;
import com.spbsu.commons.func.Action;
import com.tbts.server.xmpp.XMPPClientConnection;
import com.tbts.server.xmpp.phase.AuthorizationPhase;
import com.tbts.server.xmpp.phase.ConnectedPhase;
import com.tbts.util.akka.AkkaTools;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.BoshBody;
import com.tbts.xmpp.Item;
import com.tbts.xmpp.control.Close;
import com.tbts.xmpp.control.Open;
import scala.concurrent.duration.Duration;

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
public class BOSHSession extends UntypedActorAdapter {
  private static final Logger log = Logger.getLogger(BOSHSession.class.getName());
  private ActorRef businesLogic;
  private String id;
  private ActorRef connection;
  private Queue<Item> outgoing = new ArrayDeque<>();
  private Cancellable timeout;
  private boolean connected = false;

  public BOSHSession() {
    invoke(XMPPClientConnection.ConnectionState.AUTHORIZATION);
  }

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
      timeout = AkkaTools.scheduleTimeout(context(), Duration.create(1, TimeUnit.MINUTES), self());
    }
    else {
      invoke(XMPPClientConnection.ConnectionState.CLOSED);
      connection.tell(Collections.emptyList(), self());
      connection = null;
    }
  }

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
  public void invoke(TcpMessage cmd) {}

  public void invoke(XMPPClientConnection.ConnectionState state) {
    switch (state) {
      case AUTHORIZATION:
        businesLogic = context().actorOf(Props.create(AuthorizationPhase.class, self(), (Action<String>) id -> BOSHSession.this.id = id));
        break;
      case CONNECTED:
        connected = true;
        invoke(Timeout.zero());
        businesLogic = getContext().actorOf(Props.create(ConnectedPhase.class, self(), id));
        break;
      case CLOSED:
        businesLogic.tell(new Close(), self());
        context().stop(self());
        return;
    }
    businesLogic.tell(new Open(), self());
  }

  public void invoke(Status.Failure failure) {
    //noinspection ThrowableResultOfMethodCallIgnored
    if (failure.cause() != null)
      log.log(Level.WARNING, "", failure.cause());
    else
      log.log(Level.WARNING, failure.toString());
  }

  @SuppressWarnings("UnusedParameters")
  public void invoke(Timeout to) {
    if (connection != null) {
      connection.tell(new ArrayList<>(outgoing), self());
      outgoing.clear();
    }
    connection = null;
  }
}
