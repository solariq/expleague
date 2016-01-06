package com.tbts.server;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.Status;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.Timeout;
import com.tbts.server.xmpp.XMPPClientConnection;
import com.tbts.server.xmpp.phase.AuthorizationPhase;
import com.tbts.server.xmpp.phase.ConnectedPhase;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.BoshBody;
import com.tbts.xmpp.Item;
import com.tbts.xmpp.control.Open;
import scala.concurrent.duration.Duration;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
  private final Materializer materializer;
  private ActorRef businesLogic;
  private String id;
  private ActorRef connection;
  private Queue<Item> outgoing = new ArrayDeque<>();
  private Cancellable timeout;
  private boolean connected = false;

  public BOSHSession(Materializer materializer) {
    this.materializer = materializer;
    invoke(XMPPClientConnection.ConnectionState.AUTHORIZATION);
  }

  public void invoke(BoshBody body) {
    if (this.connection != null)
      this.connection.tell(new ArrayList(), self());
    this.connection = sender();
    if (!outgoing.isEmpty())
      invoke(Timeout.zero());

    // schedule answer in half an hour in case of no messages received
    timeout = context().system().scheduler().scheduleOnce(
        Duration.create(30, TimeUnit.MINUTES),
        self(), Timeout.zero(), context().dispatcher(), self());
    for (final Item item : body.items()) {
      businesLogic.tell(item, self());
    }
  }

  public void invoke(Item item) {
    if (item instanceof BoshBody)
      return;
    if (timeout != null)
      timeout.cancel();
    outgoing.add(item);
    if (connected) // batching
      timeout = context().system().scheduler().scheduleOnce(Duration.create(50, TimeUnit.MILLISECONDS), self(), Timeout.zero(), context().dispatcher(), self());
    else
      invoke(Timeout.zero());
  }

  public void invoke(XMPPClientConnection.ConnectionState state) {
    switch (state) {
      case AUTHORIZATION:
        businesLogic = Source.<Item>actorRef(64, OverflowStrategy.fail())
            .transform(() -> new AuthorizationPhase(id -> BOSHSession.this.id = id))
            .to(Sink.actorRef(self(), XMPPClientConnection.ConnectionState.CONNECTED))
            .run(this.materializer);
        break;
      case CONNECTED:
        connected = true;
        invoke(Timeout.zero());
        businesLogic = getContext().actorOf(Props.create(ConnectedPhase.class, id, self()));
        break;
      case CLOSED:
        context().stop(self());
        return;
    }
    businesLogic.tell(new Open(), self());
  }

  public void invoke(Status.Failure failure) {
    if (failure.cause() != null)
      log.log(Level.WARNING, "", failure.cause());
    else
      log.log(Level.WARNING, failure.toString());
  }
  public void invoke(Timeout to) {
    if (connection != null) {
      connection.tell(new ArrayList<>(outgoing), self());
      outgoing.clear();
    }
    connection = null;
  }
}
