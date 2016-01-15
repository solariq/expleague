package com.tbts.server.xmpp;

import akka.actor.*;
import akka.io.Tcp;
import akka.io.TcpMessage;
import akka.stream.OverflowStrategy;
import akka.stream.io.NegotiateNewSession;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.aalto.AsyncByteArrayFeeder;
import com.fasterxml.aalto.AsyncXMLInputFactory;
import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.fasterxml.aalto.stax.InputFactoryImpl;
import com.spbsu.commons.func.Action;
import com.spbsu.commons.io.StreamTools;
import com.spbsu.commons.net.URLConnectionTools;
import com.tbts.server.xmpp.phase.AuthorizationPhase;
import com.tbts.server.xmpp.phase.ConnectedPhase;
import com.tbts.server.xmpp.phase.HandshakePhase;
import com.tbts.server.xmpp.phase.SSLHandshake;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.util.xml.AsyncJAXBStreamReader;
import com.tbts.xmpp.Item;
import com.tbts.xmpp.Stream;
import com.tbts.xmpp.control.Close;
import com.tbts.xmpp.control.Open;
import org.xml.sax.SAXException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.xml.stream.XMLStreamException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 07.12.15
 * Time: 19:50
 */
@SuppressWarnings("unused")
public class XMPPClientConnection extends UntypedActorAdapter {
  public static final String XMPP_START = "<stream:stream xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\" xmlns=\"jabber:client\" xml:lang=\"en\" xmlns:xml=\"http://www.w3.org/XML/1998/namespace\">";
  private static final Logger log = Logger.getLogger(XMPPClientConnection.class.getName());

  private ActorRef connection;
  private SSLHelper helper;
  private boolean tls = false;
  private boolean closed = false;
  private ActorRef businessLogic;

  private String id;
  private boolean opened = false;

  public XMPPClientConnection(ActorRef connection) {
    this.connection = connection;
    try {
      invoke(ConnectionState.HANDSHAKE);
    }
    catch (SSLException e) {
      throw new RuntimeException(e);
    }
    connection.tell(TcpMessage.register(getSelf()), getSelf());
  }

  public void invoke(Tcp.Received msgIn) {
    if (currentState == ConnectionState.HANDSHAKE)
      input(msgIn.data());
    else if (currentState == ConnectionState.STARTTLS) {
      businessLogic.tell(msgIn, self());
    }
    else
      helper.decrypt(msgIn.data(), this::input);
  }

  public void invoke(Tcp.Command cmd) {
    connection.tell(cmd, self());
  }

  private AsyncXMLStreamReader<AsyncByteArrayFeeder> asyncXml;
  private AsyncJAXBStreamReader reader;
  {
    final AsyncXMLInputFactory factory = new InputFactoryImpl();
    asyncXml = factory.createAsyncForByteArray();
    reader = new AsyncJAXBStreamReader(asyncXml, Stream.jaxb());
  }

  private void input(ByteString data) {
    final byte[] copy = new byte[data.length()];
    data.asByteBuffer().get(copy);
    log.finest(">" + new String(copy, StreamTools.UTF));
    try {
      asyncXml.getInputFeeder().feedInput(copy, 0, copy.length);
      if (!opened) {
        businessLogic.tell(new Open(), self());
        opened = true;
      }
      reader.drain((in) -> {
        if (in instanceof Item)
          businessLogic.tell(in, self());
      });
    }
    catch (XMLStreamException | SAXException e) {
      log.log(Level.SEVERE, "Exception during message parsing", e);
    }
  }

  public void invoke(Item item) throws SSLException {
    final String xml;
    if (item instanceof Open)
      xml = XMPP_START;
    else
      xml = item.xmlString(false);
    log.finest("<" + xml);
    final ByteString data = ByteString.fromString(xml);
    if (currentState != ConnectionState.HANDSHAKE && currentState != ConnectionState.STARTTLS)
      helper.encrypt(data, s -> connection.tell(TcpMessage.write(s), self()));
    else
      connection.tell(TcpMessage.write(data), getSelf());
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

    final ConnectionState finalState = state;
    ActorRef newLogic = null;
    switch (state) {
      case HANDSHAKE: {
        final Source<Tcp.Received, ActorRef> source = Source.actorRef(1000, OverflowStrategy.fail());
        newLogic = context().actorOf(Props.create(HandshakePhase.class, self()), "handshake");
        break;
      }
      case STARTTLS: {
        final NegotiateNewSession firstSession = NegotiateNewSession.withDefaults().withProtocols("SSLv2Hello", "TLSv1", "TLSv1.1", "TLSv1.2");
        final SSLContext sslctxt = URLConnectionTools.prepareSSLContext4TLS();
        final SSLEngine sslEngine = sslctxt.createSSLEngine();
        sslEngine.setUseClientMode(false);
        sslEngine.setEnableSessionCreation(true);
        sslEngine.setWantClientAuth(false);
        final ActorRef handshake = getContext().actorOf(Props.create(SSLHandshake.class, self(), sslEngine), "starttls");
        sslEngine.beginHandshake();
        helper = new SSLHelper(sslEngine);
        newLogic = handshake;
        break;
      }
      case AUTHORIZATION: {
        businessLogic.tell(new Close(), self());
        newLogic = context().actorOf(Props.create(AuthorizationPhase.class, self(), (Action<String>) id -> XMPPClientConnection.this.id = id), "authorization");
        break;
      }
      case CONNECTED: {
        newLogic = getContext().actorOf(Props.create(ConnectedPhase.class, self(), id), "connected");
        break;
      }
      case CLOSED: {
        self().tell(PoisonPill.getInstance(), self());
        return;
      }
    }
    connection.tell(TcpMessage.resumeReading(), getSelf());
    opened = false;
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
