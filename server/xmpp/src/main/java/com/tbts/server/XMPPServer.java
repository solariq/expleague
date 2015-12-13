package com.tbts.server;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.io.Tcp;
import akka.io.TcpMessage;
import com.tbts.server.roster.MySQLRoster;
import com.tbts.server.xmpp.XMPPClientConnection;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.Stream;
import com.tbts.xmpp.stanza.IqH;
import com.tbts.xmpp.stanza.data.Err;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.net.InetSocketAddress;

/**
 * User: solar
 * Date: 24.11.15
 * Time: 17:42
 */
public class XMPPServer {
  private static Cfg config;
  private static Roster users;

  public static void main(String[] args) {
    final Config load = ConfigFactory.parseResourcesAnySyntax("tbts.conf").withFallback(ConfigFactory.load()).resolve();
    config = new Cfg(load);
    users = new MySQLRoster(config.db());
    Stream.jaxb();

    final IqH<Void> iq = new IqH<>();
    iq.error(new Err(Err.Cause.INSTERNAL_SERVER_ERROR, Err.ErrType.AUTH, ""));
    System.out.println(iq.toString());

//    Config config = ConfigFactory.parseString("akka.loglevel = DEBUG \n" +
//        "akka.actor.debug.lifecycle = on \n akka.event-stream = on");
//    final ActorSystem system = ActorSystem.create("TBTS_Light_XMPP", config);
//    system.actorOf(Props.create(XMPPClientIncomingStream.class));
    final ActorSystem system = ActorSystem.create("TBTS_Light_XMPP", load);
    final ActorRef actorRef = system.actorOf(Props.create(ConnectionManager.class));
    system.actorOf(Props.create(Server.class, actorRef));
  }

  public static synchronized Roster roster() {
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

  public static Cfg config() {
    return config;
  }

  public static class Cfg {
    private final String db;
    private final String domain;

    public Cfg(Config load) {
      final Config tbts = load.getConfig("tbts");
      db = tbts.getString("db");
      domain = tbts.getString("domain");
    }

    public String domain() {
      return domain;
    }

    public String db() {
      return db;
    }
  }
}
