package com.tbts.server;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.io.Tcp;
import akka.io.TcpMessage;
import com.tbts.server.xmpp.XMPPClientConnection;
import com.tbts.util.akka.UntypedActorAdapter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.net.InetSocketAddress;

/**
 * User: solar
 * Date: 24.11.15
 * Time: 17:42
 */
public class XMPPServer {
  private static Config config = new Config();
  private static UserManager users;

  public static void main(String[] args) {
    users = new UserManager() {
      @Override
      public JabberUser byName(String name) {
        return null;
      }
    };
//    Config config = ConfigFactory.parseString("akka.loglevel = DEBUG \n" +
//        "akka.actor.debug.lifecycle = on \n akka.event-stream = on");
//    final ActorSystem system = ActorSystem.create("TBTS_Light_XMPP", config);
    final ActorSystem system = ActorSystem.create("TBTS_Light_XMPP");
//    system.actorOf(Props.create(XMPPClientIncomingStream.class));
    final ActorRef actorRef = system.actorOf(Props.create(ConnectionManager.class));
    system.actorOf(Props.create(Server.class, actorRef));
  }

  public static synchronized UserManager users() {
    return users;
  }

  public static class ConnectionManager extends UntypedActor {
    @Override
    public void onReceive(Object o) throws Exception {
      System.out.println(String.valueOf(o));
    }
  }

  public static class Server extends UntypedActorAdapter {
    final ActorRef manager;

    public Server(ActorRef manager) {
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

  public static Config config() {
    return config;
  }

  public static class Config {
    public String domain() {
      return "localhost";
    }
  }
}
