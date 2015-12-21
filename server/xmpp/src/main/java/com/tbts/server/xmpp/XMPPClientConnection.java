package com.tbts.server.xmpp;

import akka.actor.*;
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
import javax.net.ssl.SSLException;
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
  private SSLHelper helper;
  private boolean tls = false;
  private boolean closed = false;
  private ActorRef businessLogic;

  private final ActorMaterializer materializer;
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

  public void invoke(Tcp.Received msgIn) {
    if (currentState != ConnectionState.HANDSHAKE && currentState != ConnectionState.STARTTLS)
      helper.decrypt(msgIn.data(), s -> businessLogic.tell(new Tcp.Received(s), self()));
    else
      businessLogic.tell(msgIn, self());
  }

  public void invoke(Tcp.Command command) throws SSLException {
    if (command instanceof Tcp.Write && currentState != ConnectionState.HANDSHAKE && currentState != ConnectionState.STARTTLS)
      helper.encrypt(((Tcp.Write) command).data(), s -> connection.tell(TcpMessage.write(s), self()));
    else
      connection.tell(command, getSelf());
  }

  public void invoke(Status.Failure failure) {
    if(businessLogic != null)
      businessLogic.tell(PoisonPill.getInstance(), self());
    log.log(Level.SEVERE, "Stream failure", failure.cause());
  }

  public void invoke(Tcp.ConnectionClosed ignore) {
    if(businessLogic != null)
      businessLogic.tell(PoisonPill.getInstance(), self());
    closed = true;
    log.fine("Client connection closed");
  }

  public void invoke(Terminated who) {
    log.finest("Terminated " + who.actor());
  }

  private ConnectionState currentState;
  public void invoke(ConnectionState state) throws SSLException {
    if (currentState == state)
      return;

    if (closed)
      state = ConnectionState.CLOSED;
    else
      connection.tell(TcpMessage.suspendReading(), getSelf());

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
        final SSLEngine sslEngine = sslctxt.createSSLEngine();
        sslEngine.setUseClientMode(false);
        sslEngine.setEnableSessionCreation(true);
        sslEngine.setWantClientAuth(false);
        final ActorRef handshake = getContext().actorOf(Props.create(SSLHandshake.class, sslEngine, getSelf()));
        sslEngine.beginHandshake();
        helper = new SSLHelper(sslEngine);
        newLogic = handshake;
        break;
      }
      case AUTHORIZATION: {
        businessLogic.tell(new Close(), self());
        businessLogic.tell(PoisonPill.getInstance(), self());
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
      case CLOSED: {
        self().tell(PoisonPill.getInstance(), self());
        return;
      }
    }
    connection.tell(TcpMessage.resumeReading(), getSelf());
    businessLogic = newLogic;
    currentState = state;
    log.fine("Connection state changed to: " + state);
    log.finest("BL changed to: " + newLogic.path());
  }

  public enum ConnectionState {
    HANDSHAKE,
    STARTTLS,
    AUTHORIZATION,
    CONNECTED,
    CLOSED
  }
}
