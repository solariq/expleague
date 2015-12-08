package com.tbts.server.xmpp;

import akka.actor.ActorRef;
import akka.io.Tcp;
import akka.io.TcpMessage;
import akka.util.ByteString;
import com.fasterxml.aalto.AsyncByteArrayFeeder;
import com.fasterxml.aalto.AsyncXMLInputFactory;
import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.fasterxml.aalto.stax.InputFactoryImpl;
import com.tbts.server.XMPPServer;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.util.xml.AsyncJAXBStreamReader;
import com.tbts.xmpp.Stanza;
import com.tbts.xmpp.Stream;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 07.12.15
 * Time: 19:50
 */
public class XMPPHandshake extends UntypedActorAdapter {
  private static final Logger log = Logger.getLogger(XMPPHandshake.class.getName());
  private final AsyncXMLStreamReader<AsyncByteArrayFeeder> asyncXml;
  private final AsyncJAXBStreamReader reader = new AsyncJAXBStreamReader("stream", "http://etherx.jabber.org/streams", Stream.class);

  public XMPPHandshake(ActorRef connection) {
    try {
      final AsyncXMLInputFactory factory = new InputFactoryImpl();
      asyncXml = factory.createAsyncForByteArray();
      connection.tell(TcpMessage.write(ByteString.fromString(
          "<stream:stream xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\" xmlns=\"jabber:client\" to=\"localhost\" xml:lang=\"en\" xmlns:xml=\"http://www.w3.org/XML/1998/namespace\">\n"
      )), getSelf());
    } catch (Exception e) {
      log.log(Level.SEVERE, "Exception during client connection init", e);
      throw new RuntimeException(e);
    }
  }

  public void invoke(Tcp.Received evt) throws IOException, XMLStreamException {
    final ByteString data = evt.data();
    byte[] copy = new byte[data.length()];
    data.asByteBuffer().get(copy);
    asyncXml.getInputFeeder().feedInput(copy, 0, copy.length);
    reader.drain(asyncXml, o -> {
      if (o instanceof Stanza)
        incoming((Stanza) o);
    });
  }

  public void invoke(Tcp.ConnectionClosed closed) {
    System.out.println("Incoming connection closed");
  }

  public void incoming(Stanza tag) {
    System.out.println(tag.toString());
  }
}
