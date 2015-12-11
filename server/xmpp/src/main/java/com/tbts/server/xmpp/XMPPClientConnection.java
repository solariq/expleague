package com.tbts.server.xmpp;

import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.io.Tcp;
import akka.io.TcpMessage;
import akka.japi.function.Function;
import akka.stream.*;
import akka.stream.io.*;
import akka.stream.javadsl.*;
import akka.util.ByteString;
import com.spbsu.commons.net.URLConnectionTools;
import com.tbts.server.xmpp.phase.AuthorizationPhase;
import com.tbts.server.xmpp.phase.HandshakePhase;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.Item;
import com.tbts.xmpp.Stream;
import scala.runtime.BoxedUnit;

import javax.net.ssl.SSLContext;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 07.12.15
 * Time: 19:50
 */
public class XMPPClientConnection extends UntypedActorAdapter {
  private static final Logger log = Logger.getLogger(XMPPClientConnection.class.getName());
  private final ActorRef connection;
  private ActorRef businessLogic;
  private final ActorMaterializer materializer;

  public XMPPClientConnection(ActorRef connection) {
    this.connection = connection;
    this.materializer = ActorMaterializer.create(
        ActorMaterializerSettings.create(getContext().system()).withSupervisionStrategy((Function<Throwable, Supervision.Directive>) param -> {
          log.log(Level.SEVERE, "Exception in the protocol flow", param);
          return Supervision.stop();
        }).withDebugLogging(true),
        getContext().system());
    invoke(ConnectionState.HANDSHAKE);
    connection.tell(TcpMessage.register(getSelf()), getSelf());
  }

  public void invoke(Tcp.Received stanza) {
    businessLogic.tell(stanza, getSelf());
  }

  public void invoke(Tcp.ConnectionClosed ignore) {
    System.out.println("Client connection closed");
  }

  public void invoke(Terminated who) {
    System.out.println("Terminated " + who.actor());
  }

  private ConnectionState currentState;
  public void invoke(ConnectionState state) {
    if (currentState == state)
      return;
    if (businessLogic != null) {
      connection.tell(TcpMessage.suspendReading(), getSelf());
      try {
        while(context().watch(businessLogic).isTerminated())
          Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
//      connection.tell(TcpMessage.resumeReading(), getSelf());
    }

    final BidiFlow<ByteString, Tcp.Command, Tcp.Received, ByteString, BoxedUnit> lowLevel = BidiFlow.fromGraph(FlowGraph.create(b -> {
      final FlowShape<Tcp.Received, ByteString> bottom = b.add(Flow.of(Tcp.Received.class).map((Function<Tcp.Received, ByteString>) (received) -> {
//        System.out.println("> [" + received.data().utf8String() + "]");
        return received.data();
      }));
      final FlowShape<ByteString, Tcp.Command> top = b.add(Flow.of(ByteString.class).map((Function<ByteString, Tcp.Command>) (data) -> {
//        System.out.println("< [" + data.utf8String() + "]");
        return TcpMessage.write(data);
      }));
      return BidiShape.fromFlows(top, bottom);
    }));

    final BidiFlow<Item, ByteString, ByteString, Item, BoxedUnit> topLevel = BidiFlow.fromGraph(FlowGraph.create(b -> {
      final FlowShape<ByteString, Item> bottom = b.add(Flow.of(ByteString.class).transform(XMPPInFlow::new));
      final FlowShape<Item, ByteString> top = b.add(Flow.of(Item.class).transform(XMPPOutFlow::new));
      return BidiShape.fromFlows(top, bottom);
    }));

    ActorRef newLogic = null;
    switch (state) {
      case HANDSHAKE: {
        final BidiFlow<Item, Tcp.Command, Tcp.Received, Item, BoxedUnit> protocol = topLevel.atop(lowLevel);
        final Flow<Tcp.Received, Tcp.Command, BoxedUnit> finalFlow = protocol.reversed().join(Flow.of(Item.class).transform(
            () -> new HandshakePhase(getSelf())
        ));
        final Source<Tcp.Received, ActorRef> source = Source.actorRef(100, OverflowStrategy.fail());
        newLogic = source.via(finalFlow).to(Sink.actorRef(connection, TcpMessage.resumeReading())).run(materializer);
        break;
      }
      case AUTHORIZATION: {
        log.log(Level.FINE, "Starting TLS");
        final NegotiateNewSession firstSession = NegotiateNewSession.withDefaults().withProtocols("SSLv2Hello", "TLSv1", "TLSv1.1", "TLSv1.2");
        final SSLContext sslctxt = URLConnectionTools.prepareSSLContext4TLS();

        final BidiFlow<SslTlsOutbound, ByteString, ByteString, SslTlsInbound, BoxedUnit> tlsLevel = SslTls.create(sslctxt, firstSession, Role.server());
        final BidiFlow<ByteString, SslTlsOutbound, SslTlsInbound, ByteString, BoxedUnit> tlsAdapterLevel = BidiFlow.fromGraph(FlowGraph.create(b -> {
          final FlowShape<ByteString, SslTlsOutbound> top = b.add(Flow.of(ByteString.class).map(SendBytes::apply));
          final FlowShape<SslTlsInbound, ByteString> bottom = b.add(Flow.of(SslTlsInbound.class).map(x -> {
            if (x instanceof SessionBytes)
              return ((SessionBytes) x).bytes();
            else
              return ByteString.empty();
          }));
          return BidiShape.fromFlows(top, bottom);
        }));

        final BidiFlow<Item, Tcp.Command, Tcp.Received, Item, BoxedUnit> protocol = topLevel.atop(tlsAdapterLevel).atop(tlsLevel).atop(lowLevel);

        final Flow<Tcp.Received, Tcp.Command, BoxedUnit> finalFlow = protocol.reversed().join(Flow.of(Item.class).transform(
            () -> new AuthorizationPhase(getSelf())
        ));
        final Source<Tcp.Received, ActorRef> source = Source.actorRef(100, OverflowStrategy.fail());
        newLogic = source.via(finalFlow).to(Sink.actorRef(connection, TcpMessage.resumeReading())).run(materializer);
        break;

//        final SSLContext sslContext = SSLContext.getDefault();
//        SslTls.apply(sslContext, NegotiateNewSession.withDefaults(), Role.server(), Closing.eagerClose(), Option.empty()).atop()
      }
      case CONNECTED:
        break;
      case CLOSED:
        break;
    }
//    if (businessLogic != null)
//      connection.tell(TcpMessage.resumeReading(), getSelf());
    businessLogic = newLogic;
    System.out.println("BL changed to: " + newLogic.path());

    currentState = state;
  }

  public enum ConnectionState {
    HANDSHAKE,
    CONNECTED,
    AUTHORIZATION,
    CLOSED
  }

}
