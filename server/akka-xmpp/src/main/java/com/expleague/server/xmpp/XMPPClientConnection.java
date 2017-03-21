package com.expleague.server.xmpp;

import akka.actor.*;
import akka.io.Tcp;
import akka.io.TcpMessage;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.xmpp.phase.AuthorizationPhase;
import com.expleague.server.xmpp.phase.ConnectedPhase;
import com.expleague.server.xmpp.phase.HandshakePhase;
import com.expleague.server.xmpp.phase.SSLHandshake;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.util.xml.AsyncJAXBStreamReader;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.Stream;
import com.expleague.xmpp.control.Close;
import com.expleague.xmpp.control.Open;
import com.fasterxml.aalto.AsyncByteArrayFeeder;
import com.fasterxml.aalto.AsyncXMLInputFactory;
import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.fasterxml.aalto.stax.InputFactoryImpl;
import com.relayrides.pushy.apns.P12Util;
import com.spbsu.commons.func.Action;
import com.spbsu.commons.io.StreamTools;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import org.xml.sax.SAXException;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 07.12.15
 * Time: 19:50
 */
@SuppressWarnings("unused")
public class XMPPClientConnection extends ActorAdapter<UntypedActor> {
  private static final Logger log = Logger.getLogger(XMPPClientConnection.class.getName());
  private static boolean unitTestEnabled = false;

  private ActorRef connection;
  private SSLHelper helper;
  private boolean tls = false;
  private boolean closed = false;
  private ActorRef businessLogic;

  private String id;
  private boolean opened = false;

  public XMPPClientConnection(ActorRef connection) {
    this.connection = connection;
  }

  @Override
  protected void init() {
    invoke(ConnectionState.HANDSHAKE);
    connection.tell(TcpMessage.register(self()), self());
  }

  @ActorMethod
  public void invoke(Tcp.Received msgIn) {
    if (currentState == ConnectionState.HANDSHAKE)
      input(msgIn.data());
    else if (currentState == ConnectionState.STARTTLS)
      businessLogic.tell(msgIn, self());
    else
      helper.decrypt(msgIn.data(), this::input);
  }

  @ActorMethod
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
    if (data == null) {
      invoke(ConnectionState.CLOSED);
      return;
    }

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

  @ActorMethod
  public void invoke(Item item) throws SSLException {
    Tcp.Event requestedAck = new Tcp.NoAck(null);
    final String xml;
    if (item instanceof Open)
      xml = Item.XMPP_START;
    else
      xml = item.xmlString(false);

    log.finest("<" + xml);
    final ByteString data = ByteString.fromString(xml);
    if (currentState != ConnectionState.HANDSHAKE && currentState != ConnectionState.STARTTLS)
      helper.encrypt(data, this::output);
    else
      output(data);
  }

  private static class Ack implements Tcp.Event {
    private static volatile int ackCounter = 0;
    final int id;
    final int size;
    Ack(int size) {
      this.id = ackCounter++;
      this.size = size;
    }
  }

  private Ack current;
  private final Queue<ByteString> outQueue = new ArrayDeque<>();
  private void output(ByteString out) {
    if (current == null)
      connection.tell(new Tcp.Write(out, current = new Ack(out.size())), self());
    else
      outQueue.add(out);
  }

  @ActorMethod
  public void nextMessage(Ack ack) {
//    log.finest("Sent packet " + ack.id + " (" + ack.size +  " bytes) " + (outQueue.isEmpty() ? "" : (outQueue.size() + " more in queue")));
    if (current == null || ack.id == current.id) {
      current = null;
      final ByteString next = outQueue.poll();
      if (next != null)
        connection.tell(new Tcp.Write(next, current = new Ack(next.size())), self());
    }
  }

  @ActorMethod
  public void failedToSend(Tcp.CommandFailed failed) {
    log.warning("Unable to send message to " + id + " stopping connection");
    if(businessLogic != null)
      businessLogic.tell(PoisonPill.getInstance(), self());
    else context().stop(self());
  }

  @ActorMethod
  public void invoke(Status.Failure failure) {
    if(businessLogic != null)
      businessLogic.tell(PoisonPill.getInstance(), self());
    log.log(Level.SEVERE, "Stream failure", failure.cause());
  }

  @ActorMethod
  public void invoke(Tcp.ConnectionClosed ignore) {
    if(businessLogic != null)
      businessLogic.tell(PoisonPill.getInstance(), self());
    closed = true;
    log.fine("Client connection closed");
  }

  @ActorMethod
  public void invoke(Terminated who) {
    log.finest("Terminated " + who.actor());
  }

  private ConnectionState currentState;

  @ActorMethod
  public void invoke(ConnectionState state) {
    if (currentState == state)
      return;

    if (closed)
      state = ConnectionState.CLOSED;

    final ConnectionState finalState = state;
    ActorRef newLogic = null;
    switch (state) {
      case HANDSHAKE: {
        final Source<Tcp.Received, ActorRef> source = Source.actorRef(1000, OverflowStrategy.fail());
        newLogic = context().actorOf(props(HandshakePhase.class, self()), "handshake");
        break;
      }
      case STARTTLS: {
        try {
          final String domain = ExpLeagueServer.config().domain();
          final File file;
          if (unitTestEnabled) {
            final ClassLoader classLoader = getClass().getClassLoader();
            //noinspection ConstantConditions
            file = new File(classLoader.getResource(domain + ".p12").getFile());
          } else {
            file = new File("./certs/" + domain + ".p12");
          }

          if (!file.exists()) {
            synchronized (XMPPClientConnection.class) {
              if (!file.exists()) {
                final File script = File.createTempFile("create-self-signed", ".sh");
                StreamTools.transferData(getClass().getResourceAsStream("/create-self-signed.sh"), new FileOutputStream(script));
                //noinspection ResultOfMethodCallIgnored
                file.getParentFile().mkdirs();
                final Process exec = Runtime.getRuntime().exec("/bin/bash");
                final PrintStream bash = new PrintStream(exec.getOutputStream());
                bash.println("cd " + file.getParentFile().getAbsolutePath());
                bash.println("bash " + script.getAbsolutePath() + " " + domain);
                exec.getOutputStream().close();
                exec.waitFor();
                log.info(StreamTools.readStream(exec.getInputStream()).toString());
                log.warning(StreamTools.readStream(exec.getErrorStream()).toString());
              }
            }
          }
          final SslContext context = getSslContextWithP12File(file, "");
          final SSLEngine sslEngine = context.newEngine(ByteBufAllocator.DEFAULT);
          sslEngine.setUseClientMode(false);
//          sslEngine.setEnableSessionCreation(true);
          sslEngine.setWantClientAuth(false);
          final ActorRef handshake = context().actorOf(props(SSLHandshake.class, self(), sslEngine), "starttls");
          sslEngine.beginHandshake();
          helper = new SSLHelper(sslEngine);
          newLogic = handshake;
        }
        catch (IOException | InterruptedException ioe) {
          log.log(Level.SEVERE, "Unable to create SSL context", ioe);
        }
        break;
      }
      case AUTHORIZATION: {
        { // reset factory to be able to work with <?xml?> instructions
          final AsyncXMLInputFactory factory = new InputFactoryImpl();
          asyncXml = factory.createAsyncForByteArray();
          reader = new AsyncJAXBStreamReader(asyncXml, Stream.jaxb());
        }

        businessLogic.tell(new Close(), self());
        final Object[] args = new Object[]{self(), (Action<String>) id -> XMPPClientConnection.this.id = id};
        newLogic = context().actorOf(props(AuthorizationPhase.class, args), "authorization");
        break;
      }
      case CONNECTED: {
        { // reset factory to be able to work with <?xml?> instructions
          final AsyncXMLInputFactory factory = new InputFactoryImpl();
          asyncXml = factory.createAsyncForByteArray();
          reader = new AsyncJAXBStreamReader(asyncXml, Stream.jaxb());
        }
        newLogic = context().actorOf(props(ConnectedPhase.class, self(), id), "connected");
        break;
      }
      case CLOSED: {
        connection.tell(PoisonPill.getInstance(), self());
        self().tell(PoisonPill.getInstance(), self());
        return;
      }
    }
    connection.tell(TcpMessage.resumeReading(), self());
    opened = false;
    businessLogic = newLogic;
    currentState = state;
    log.fine("Connection state changed to: " + state);
    log.finest("BL changed to: " + (newLogic != null ? newLogic.path() : null));
  }

  private static SslContext getSslContextWithP12File(final File p12File, final String password) throws SSLException {
    final X509Certificate x509Certificate;
    final PrivateKey privateKey;

    try {
      final KeyStore.PrivateKeyEntry privateKeyEntry = P12Util.getPrivateKeyEntryFromP12File(p12File, password);

      final Certificate certificate = privateKeyEntry.getCertificate();

      if (!(certificate instanceof X509Certificate)) {
        throw new KeyStoreException("Found a certificate in the provided PKCS#12 file, but it was not an X.509 certificate.");
      }

      x509Certificate = (X509Certificate) certificate;
      privateKey = privateKeyEntry.getPrivateKey();
    } catch (final KeyStoreException | IOException | CertificateException | UnrecoverableEntryException | NoSuchAlgorithmException e) {
      throw new SSLException(e);
    }

    return getSslContextWithCertificateAndPrivateKey(x509Certificate, privateKey, password);
  }

  private static SslContext getSslContextWithCertificateAndPrivateKey(final X509Certificate certificate, final PrivateKey privateKey, final String privateKeyPassword) throws SSLException {
    return SslContextBuilder.forServer(privateKey, privateKeyPassword, certificate)
        .sslProvider(unitTestEnabled ? SslProvider.JDK : SslProvider.OPENSSL)
//        .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
        .build();
  }

  public static void setUnitTestEnabled(boolean unitTestEnabled) {
    XMPPClientConnection.unitTestEnabled = unitTestEnabled;
  }

  public enum ConnectionState {
    HANDSHAKE,
    STARTTLS,
    AUTHORIZATION,
    CONNECTED,
    CLOSED
  }
}
