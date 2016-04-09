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
import com.expleague.util.akka.UntypedActorAdapter;
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
import java.util.ArrayList;
import java.util.List;
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
    sendItem(item, new Tcp.NoAck(null));
  }

  public void sendItem(Item item, Tcp.Event requestedAck) throws SSLException {
    final String xml;
    if (item instanceof Open)
      xml = Item.XMPP_START;
    else
      xml = item.xmlString(false);

    log.finest("<" + xml);
    final ByteString data = ByteString.fromString(xml);
    if (currentState != ConnectionState.HANDSHAKE && currentState != ConnectionState.STARTTLS) {
      final List<ByteString> encrypted = new ArrayList<>();
      helper.encrypt(data, encrypted::add);
      final int size = encrypted.size();
      for (int i = 0; i < size; i++) {
        final Tcp.Command write;
        if (i < size - 1)
          write = TcpMessage.write(encrypted.get(i));
        else
          write = TcpMessage.write(encrypted.get(i), requestedAck);
        connection.tell(write, self());
      }
    }
    else
      connection.tell(TcpMessage.write(data, requestedAck), getSelf());
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
        try {
          final String domain = ExpLeagueServer.config().domain();
          final File file = new File("./certs/" + domain + ".p12");
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
          final ActorRef handshake = getContext().actorOf(Props.create(SSLHandshake.class, self(), sslEngine), "starttls");
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
        newLogic = context().actorOf(Props.create(AuthorizationPhase.class, self(), (Action<String>) id -> XMPPClientConnection.this.id = id), "authorization");
        break;
      }
      case CONNECTED: {
        { // reset factory to be able to work with <?xml?> instructions
          final AsyncXMLInputFactory factory = new InputFactoryImpl();
          asyncXml = factory.createAsyncForByteArray();
          reader = new AsyncJAXBStreamReader(asyncXml, Stream.jaxb());
        }
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
        .sslProvider(SslProvider.OPENSSL)
//        .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
        .build();
  }

  public enum ConnectionState {
    HANDSHAKE,
    STARTTLS,
    AUTHORIZATION,
    CONNECTED,
    CLOSED
  }
}
