package com.expleague.server;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.io.Tcp;
import akka.io.TcpMessage;
import akka.io.TcpSO;
import com.expleague.server.xmpp.XMPPClientConnection;
import com.expleague.util.akka.UntypedActorAdapter;

import java.net.InetSocketAddress;
import java.util.Arrays;
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
    //noinspection ArraysAsListWithZeroOrOneArgument
    //todo: how about ipv6?
    tcp.tell(TcpMessage.bind(getSelf(), new InetSocketAddress("0.0.0.0", 5222), 100, Arrays.asList(
        TcpSO.keepAlive(true)
        ), false), getSelf());
  }

  public void invoke(Tcp.Event msg) {
    log.fine(String.valueOf(msg));
    if (msg instanceof Tcp.CommandFailed)
      getContext().stop(getSelf());
    else if (msg instanceof Tcp.Connected)
      getContext().actorOf(Props.create(XMPPClientConnection.class, getSender()));
  }
}
