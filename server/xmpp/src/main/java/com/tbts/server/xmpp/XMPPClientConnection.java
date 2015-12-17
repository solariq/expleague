package com.tbts.server.xmpp;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.Terminated;
import akka.io.Tcp;
import akka.io.TcpMessage;
import akka.japi.function.Function;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.OverflowStrategy;
import akka.stream.Supervision;
import akka.stream.io.NegotiateNewSession;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.spbsu.commons.net.URLConnectionTools;
import com.tbts.server.xmpp.phase.AuthorizationPhase;
import com.tbts.server.xmpp.phase.ConnectedPhase;
import com.tbts.server.xmpp.phase.HandshakePhase;
import com.tbts.server.xmpp.phase.SSLHandshake;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.Item;
import com.tbts.xmpp.control.Close;
import com.tbts.xmpp.control.Open;
import scala.runtime.BoxedUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 07.12.15
 * Time: 19:50
 */
@SuppressWarnings("unused")
public class XMPPClientConnection extends UntypedActorAdapter {
  private static final Logger log = Logger.getLogger(XMPPClientConnection.class.getName());

  private ActorRef connection;
  private boolean tls = false;
  private ActorRef businessLogic;

  private final ActorMaterializer materializer;
  private SSLEngine sslEngine;
  private String id;

  public XMPPClientConnection(ActorRef connection) {
    this.connection = connection;
    this.materializer = ActorMaterializer.create(
        ActorMaterializerSettings.create(getContext().system())
            .withSupervisionStrategy((Function<Throwable, Supervision.Directive>) param -> {
              log.log(Level.SEVERE, "Exception in the protocol flow", param);
              return Supervision.stop();
            })
            .withDebugLogging(true)
            .withInputBuffer(1<<6, 1<<7),
        getContext().system());
    try {
      invoke(ConnectionState.HANDSHAKE);
    }
    catch (SSLException e) {
      throw new RuntimeException(e);
    }
    connection.tell(TcpMessage.register(getSelf()), getSelf());
  }

  private ByteBuffer inDst = ByteBuffer.allocate(4096);
  private ByteBuffer inSrc = ByteBuffer.allocate(4096);
  public void invoke(Tcp.Received msgIn) {
    if (currentState == ConnectionState.HANDSHAKE || currentState == ConnectionState.STARTTLS) {
      businessLogic.tell(msgIn, getSelf());
      return;
    }
    try {
      inSrc.put(msgIn.data().asByteBuffer());
      while (inSrc.position() > 0) {
        inSrc.flip();
        final SSLEngineResult r = sslEngine.unwrap(inSrc, inDst);
        switch (r.getStatus()) {
          case BUFFER_OVERFLOW: {
            // Could attempt to drain the dst buffer of any already obtained
            // data, but we'll just increase it to the size needed.
            int appSize = sslEngine.getSession().getApplicationBufferSize();
            final ByteBuffer b = ByteBuffer.allocate(appSize + inDst.position());
            inDst.flip();
            b.put(inDst);
            inDst = b;
            // retry the operation.
            invoke(msgIn);
            break;
          }
          case BUFFER_UNDERFLOW: {
            int netSize = sslEngine.getSession().getPacketBufferSize();
            // Resize buffer if needed.
            if (netSize > inDst.capacity()) {
              final ByteBuffer b = ByteBuffer.allocate(netSize);
              inSrc.flip();
              b.put(inSrc);
              inSrc = b;
            }
            // Obtain more inbound network data for inSrc,
            // then retry the operation.
            return;
            // other cases: CLOSED, OK.
          }
        }
        inDst.flip();
        if (inDst.limit() > 0) {
          final ByteString data = ByteString.fromByteBuffer(inDst);
          businessLogic.tell(new Tcp.Received(data), getSelf());
          inDst.clear();
        }
        inSrc.compact();
      }
    }
    catch (SSLException e) {
      throw new RuntimeException(e);
    }
  }

  public void invoke(Status.Failure failure) {
    log.log(Level.SEVERE, "Stream failure", failure.cause());
  }

  private ByteBuffer outDst = ByteBuffer.allocate(4096);
  private ByteBuffer outSrc = ByteBuffer.allocate(4096);
  public void invoke(Tcp.Command command) throws SSLException {
    if (command instanceof Tcp.Write && sslEngine != null && currentState != ConnectionState.STARTTLS) {
      final ByteString data = ((Tcp.Write) command).data();
      if (outSrc.remaining() < data.length())
        outSrc = SSLHandshake.expandBuffer(outSrc, data.length());
      data.copyToBuffer(outSrc);
      outSrc.flip();
      while (outSrc.remaining() > 0) {
        final SSLEngineResult result = sslEngine.wrap(outSrc, outDst);
        switch (result.getStatus()) {
          case BUFFER_OVERFLOW:
            outDst = SSLHandshake.expandBuffer(outDst, sslEngine.getSession().getPacketBufferSize());
            break;
          case BUFFER_UNDERFLOW:
            throw new RuntimeException("Strange happened!");
          default:
            outDst.flip();
            connection.tell(TcpMessage.write(ByteString.fromByteBuffer(outDst)), getSelf());
            outDst.clear();
        }
      }
      outSrc.clear();
//      System.out.println("<" + data.utf8String());
    }
    else connection.tell(command, getSelf());
  }

  public void invoke(Tcp.ConnectionClosed ignore) {
    System.out.println("Client connection closed");
  }

  public void invoke(Terminated who) {
    System.out.println("Terminated " + who.actor());
  }

  private ConnectionState currentState;
  public void invoke(ConnectionState state) throws SSLException {
    if (currentState == state)
      return;
    if (businessLogic != null) {
      connection.tell(TcpMessage.suspendReading(), getSelf());
      try {
        while(!context().watch(businessLogic).isTerminated())
          Thread.sleep(100);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    final Flow<Tcp.Received, Item, BoxedUnit> inFlow = Flow.of(Tcp.Received.class).map(Tcp.Received::data).transform(XMPPInFlow::new);
    final Flow<Item, Tcp.Command, BoxedUnit> outFlow = Flow.of(Item.class).transform(XMPPOutFlow::new).map(TcpMessage::write);

    ActorRef newLogic = null;
    switch (state) {
      case HANDSHAKE: {
        final Source<Tcp.Received, ActorRef> source = Source.actorRef(1000, OverflowStrategy.fail());
        newLogic = source
            .via(inFlow)
            .transform(HandshakePhase::new)
            .via(outFlow)
            .to(Sink.actorRef(getSelf(), ConnectionState.STARTTLS))
            .run(materializer);
        break;
      }
      case STARTTLS: {
        final NegotiateNewSession firstSession = NegotiateNewSession.withDefaults().withProtocols("SSLv2Hello", "TLSv1", "TLSv1.1", "TLSv1.2");
        final SSLContext sslctxt = URLConnectionTools.prepareSSLContext4TLS();
        sslEngine = sslctxt.createSSLEngine();
        sslEngine.setUseClientMode(false);
        sslEngine.setEnableSessionCreation(true);
        sslEngine.setWantClientAuth(false);
        final ActorRef handshake = getContext().actorOf(Props.create(SSLHandshake.class, sslEngine, getSelf()));
        sslEngine.beginHandshake();
        newLogic = handshake;
        break;
      }
      case AUTHORIZATION: {
        newLogic = Source.<Tcp.Received>actorRef(64, OverflowStrategy.dropHead())
            .via(inFlow)
            .transform(() -> new AuthorizationPhase(id -> XMPPClientConnection.this.id = id))
            .via(outFlow)
            .to(Sink.actorRef(self(), ConnectionState.CONNECTED))
            .run(materializer);
        break;
      }
      case CONNECTED: {
        final ActorRef inFlowActor = Source.<Item>actorRef(64, OverflowStrategy.fail())
            .via(outFlow)
            .to(Sink.actorRef(self(), ConnectionState.CLOSED))
            .run(materializer);
        final ActorRef connectionLogic = getContext().actorOf(Props.create(ConnectedPhase.class, id, inFlowActor));
        newLogic = Source.<Tcp.Received>actorRef(1000, OverflowStrategy.fail())
            .via(inFlow)
            .to(Sink.actorRef(
                connectionLogic, new Close()))
            .run(materializer);
        connectionLogic.tell(new Open(), getSelf());
        break;
      }
      case CLOSED:
        break;
    }
    if (businessLogic != null)
      connection.tell(TcpMessage.resumeReading(), getSelf());
    businessLogic = newLogic;
    System.out.println("BL changed to: " + newLogic.path());

    currentState = state;
  }

  public enum ConnectionState {
    HANDSHAKE,
    STARTTLS,
    AUTHORIZATION,
    CONNECTED,
    CLOSED
  }
}
