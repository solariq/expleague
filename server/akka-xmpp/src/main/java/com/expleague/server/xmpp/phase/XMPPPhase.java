package com.expleague.server.xmpp.phase;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import akka.io.TcpMessage;
import com.expleague.server.xmpp.XMPPClientConnection;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.control.Open;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 08.12.15
 * Time: 17:15
 */
public abstract class XMPPPhase extends ActorAdapter<UntypedActor> {
  private static final Logger log = Logger.getLogger(XMPPPhase.class.getName());
  private final ActorRef connection;

  protected XMPPPhase(ActorRef connection) {
    this.connection = connection;
  }

  public void unhandled(Object msg) {
    log.log(Level.WARNING, "Unexpected xmpp item: " + msg);
  }
  protected void answer(Item item) {
    connection.tell(item, self());
  }

  @ActorMethod
  public final void invoke(Open ignore) {
    connection.tell(ignore, self());
    open();
  }

  public abstract void open();

  public void last(Item msg, XMPPClientConnection.ConnectionState state) {
    log.finest("Finishing phase " + self().path().name());
    connection.tell(TcpMessage.suspendReading(), self());
    connection.tell(msg, self());
    connection.tell(state, self());
  }
}
