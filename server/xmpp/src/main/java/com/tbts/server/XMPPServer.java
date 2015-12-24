package com.tbts.server;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.io.Tcp;
import akka.io.TcpMessage;
import com.tbts.server.xmpp.XMPPClientConnection;
import com.tbts.util.akka.UntypedActorAdapter;

import java.net.InetSocketAddress;

/**
 * User: solar
 * Date: 24.12.15
 * Time: 14:47
 */
public class XMPPServer extends UntypedActorAdapter {
  final ActorRef manager;

  public XMPPServer(ActorRef manager) {
    this.manager = manager;
  }

  @Override
  public void preStart() throws Exception {
    final ActorRef tcp = Tcp.get(getContext().system()).manager();
    tcp.tell(TcpMessage.bind(getSelf(), new InetSocketAddress("localhost", 5222), 100), getSelf());
  }

  public void invoke(Tcp.Event msg) {
    if (msg instanceof Tcp.Bound) {
      manager.tell(msg, getSelf());
    }
    else if (msg instanceof Tcp.CommandFailed) {
      getContext().stop(getSelf());
    }
    else if (msg instanceof Tcp.Connected) {
      final Tcp.Connected conn = (Tcp.Connected) msg;
      manager.tell(conn, getSelf());
      getContext().actorOf(Props.create(XMPPClientConnection.class, getSender()));
    }
  }
}
