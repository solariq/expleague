package com.tbts.server.xmpp;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.io.Tcp;
import akka.io.TcpMessage;
import akka.util.ByteString;

import java.net.InetSocketAddress;

/**
 * User: solar
 * Date: 24.11.15
 * Time: 17:42
 */
public class XMPPServer {
  public static void main(String[] args) {
    final ActorSystem system = ActorSystem.create("TBTS_Light_XMPP");
    final ActorRef actorRef = system.actorOf(Props.create(ConnectionManager.class));
    system.actorOf(Props.create(Server.class, actorRef));
  }

  public static class ConnectionManager extends UntypedActor {
    @Override
    public void onReceive(Object o) throws Exception {
      System.out.println(String.valueOf(o));
    }
  }

  public static class Server extends UntypedActor {
    final ActorRef manager;

    public Server(ActorRef manager) {
      this.manager = manager;
    }

    @Override
    public void preStart() throws Exception {
      final ActorRef tcp = Tcp.get(getContext().system()).manager();
      tcp.tell(TcpMessage.bind(getSelf(), new InetSocketAddress("localhost", 5222), 100), getSelf());
    }

    @Override
    public void onReceive(Object msg) throws Exception {
      if (msg instanceof Tcp.Bound) {
        manager.tell(msg, getSelf());
      }
      else if (msg instanceof Tcp.CommandFailed) {
        getContext().stop(getSelf());
      }
      else if (msg instanceof Tcp.Connected) {
        final Tcp.Connected conn = (Tcp.Connected) msg;
        manager.tell(conn, getSelf());
        final ActorRef handler = getContext().actorOf(Props.create(XMPPHandler.class));
        getSender().tell(TcpMessage.register(handler), getSelf());
      }
    }
  }

  public static class XMPPHandler extends UntypedActor {
    @Override
    public void onReceive(Object msg) throws Exception {
      if (msg instanceof Tcp.Received) {
        final ByteString data = ((Tcp.Received) msg).data();
        System.out.println(data);
        getSender().tell(TcpMessage.write(data), getSelf());
      }
      else if (msg instanceof Tcp.ConnectionClosed) {
        getContext().stop(getSelf());
      }
    }
  }
}
