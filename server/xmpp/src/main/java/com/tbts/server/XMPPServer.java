package com.tbts.server;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.io.Tcp;
import akka.io.TcpMessage;
import akka.util.ByteString;
import com.tbts.server.xmpp.XMPPHandshake;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.Stream;
import com.tbts.xmpp.Stanza;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.StringWriter;
import java.net.InetSocketAddress;

/**
 * User: solar
 * Date: 24.11.15
 * Time: 17:42
 */
public class XMPPServer {
  public static void main(String[] args) {
    final ActorSystem system = ActorSystem.create("TBTS_Light_XMPP");
//    system.actorOf(Props.create(XMPPClientIncomingStream.class));
    final ActorRef actorRef = system.actorOf(Props.create(ConnectionManager.class));
    system.actorOf(Props.create(Server.class, actorRef));
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
      tcp.tell(TcpMessage.bind(getSelf(), new InetSocketAddress("localhost", 5223), 100), getSelf());
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
        final ActorRef handshake = getContext().actorOf(Props.create(XMPPHandshake.class, getSender()));
        getSender().tell(TcpMessage.register(handshake), getSelf());
      }
    }
  }

  //  public static class XMPPClientIncomingStream extends UntypedActorAdapter {
//    private static final Logger log = Logger.getLogger(XMPPClientIncomingStream.class.getName());
//    private final AsyncXMLStreamReader<AsyncByteArrayFeeder> asyncXml;
//    private final AsyncJAXBStreamReader reader = new AsyncJAXBStreamReader("stream", "http://etherx.jabber.org/streams", Stream.class);
//
//    public XMPPClientIncomingStream(ActorRef connection) {
//      try {
//        final AsyncXMLInputFactory factory = new InputFactoryImpl();
//        asyncXml = factory.createAsyncForByteArray();
//      }
//      catch (Exception e) {
//        log.log(Level.SEVERE, "Exception during client connection init", e);
//        throw new RuntimeException(e);
//      }
//    }
//    public void invoke(Tcp.Received evt) throws IOException, XMLStreamException {
//      final ByteString data = evt.data();
//      byte[] copy = new byte[data.length()];
//      data.asByteBuffer().get(copy);
//      asyncXml.getInputFeeder().feedInput(copy, 0, copy.length);
//      reader.drain(asyncXml, o -> {
//        if (o instanceof Stanza)
//          incoming((Stanza)o);
//      });
//    }
//
//    public void invoke(Tcp.ConnectionClosed closed) {
//      System.out.println("Incoming connection closed");
//    }
//
//    public void incoming(Stanza tag) {
//      System.out.println(tag.toString());
//    }
//  }

  public static class XMPPClientOutgoingStream extends UntypedActorAdapter {
    private final ActorRef connection;
    private final Marshaller marshaller;


    public XMPPClientOutgoingStream(ActorRef connection) {
      this.connection = connection;
      try {
        final JAXBContext context = JAXBContext.newInstance(Stream.class);
        marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
      } catch (JAXBException e) {
        throw new RuntimeException(e);
      }

    }

    public void invoke(Stanza stanza) {
      try (final StringWriter writer = new StringWriter(100)){
        //noinspection unchecked
        marshaller.marshal(new JAXBElement<>(new QName(stanza.ns(), stanza.name()), (Class<Stanza>)stanza.getClass(), stanza), writer);
        writer.close();
        connection.tell(TcpMessage.write(ByteString.fromString(writer.toString())), getSelf());
      }
      catch (JAXBException | IOException e) {
        throw new RuntimeException(e);
      }
    }

    public void invoke(Tcp.ConnectionClosed closed) {
      System.out.println("Outgoing connection closed");
    }
  }
}
