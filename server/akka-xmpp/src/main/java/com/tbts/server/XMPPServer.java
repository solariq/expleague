package com.tbts.server;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.io.Tcp;
import akka.io.TcpMessage;
import com.tbts.server.xmpp.XMPPClientConnection;
import com.tbts.util.akka.UntypedActorAdapter;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 24.12.15
 * Time: 14:47
 */
public class XMPPServer extends UntypedActorAdapter {
  private static final Logger log = Logger.getLogger(XMPPServer.class.getName());
  @Override
  public void preStart() throws Exception {
    final ActorRef tcp = Tcp.get(getContext().system()).manager();
    tcp.tell(TcpMessage.bind(getSelf(), new InetSocketAddress("localhost", 5222), 100), getSelf());
  }

  public void invoke(Tcp.Event msg) {
    log.fine(String.valueOf(msg));
    if (msg instanceof Tcp.CommandFailed)
      getContext().stop(getSelf());
    else if (msg instanceof Tcp.Connected)
      getContext().actorOf(Props.create(XMPPClientConnection.class, getSender()));
  }
}
