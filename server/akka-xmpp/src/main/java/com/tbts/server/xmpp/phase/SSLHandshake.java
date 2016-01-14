package com.tbts.server.xmpp.phase;

import akka.actor.ActorRef;
import akka.io.Tcp;
import akka.io.TcpMessage;
import akka.util.ByteString;
import com.tbts.server.xmpp.XMPPClientConnection;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.control.Close;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 12.12.15
 * Time: 21:17
 */
public class SSLHandshake extends UntypedActorAdapter {
  private static final Logger log = Logger.getLogger(SSLHandshake.class.getName());
  private final SSLEngine sslEngine;
  private final ActorRef connection;

  public SSLHandshake(ActorRef connection, SSLEngine sslEngine) {
    this.sslEngine = sslEngine;
    this.connection = connection;
  }

  boolean finished = false;
  private ByteBuffer in = ByteBuffer.allocate(4096);
  private ByteBuffer out = ByteBuffer.allocate(4096);
  private ByteBuffer toSend = ByteBuffer.allocate(4096);
  public void invoke(Close close) {
    if (in.position() != 0) {
      in.flip();
      sender().tell(new Tcp.Received(ByteString.fromByteBuffer(in)), self());
    }
    context().stop(self());
  }

  public void invoke(Tcp.Received received) throws SSLException {
//    System.out.println("in: [" + received.data().mkString() + "]");
    if (in.remaining() < received.data().length()) {
      in = expandBuffer(in, received.data().length());
    }
    received.data().copyToBuffer(in);
    if (finished)
      return;

    SSLEngineResult.HandshakeStatus hsStatus = sslEngine.getHandshakeStatus();
    while (true) {
      SSLEngineResult res;
//      System.out.println(hsStatus.toString());

      switch (hsStatus) {
        case FINISHED:
          send();
          connection.tell(XMPPClientConnection.ConnectionState.AUTHORIZATION, self());
          finished = true;
          if (out.position() != 0)
            throw new RuntimeException("Buffer overflow after handshake");
          return;
        case NEED_TASK:
          Runnable task;
          while ((task = sslEngine.getDelegatedTask()) != null)
            task.run();
          hsStatus = sslEngine.getHandshakeStatus();
          break;

        case NEED_UNWRAP:
          in.flip();
          res = sslEngine.unwrap(in, out);
          final SSLEngineResult.Status status = res.getStatus();
          if (status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
            send();
            return; // waiting for more data
          }
          if (status != SSLEngineResult.Status.BUFFER_OVERFLOW) {
            hsStatus = res.getHandshakeStatus();
            out.clear();
            in.compact();
          }
          else {
            out = expandBuffer(out, sslEngine.getSession().getApplicationBufferSize());
            log.info("Unwrap buffer overflow. Pos:" + in.position());
          }
          break;
        case NEED_WRAP:
          // First make sure that the out buffer is completely empty. Since we
          // cannot call wrap with data left on the buffer
          res = sslEngine.wrap(ByteBuffer.allocate(0), toSend);
          if (res.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW)
            toSend = expandBuffer(toSend, sslEngine.getSession().getPacketBufferSize());
          hsStatus = res.getHandshakeStatus();
          break;

        case NOT_HANDSHAKING:
          throw new IllegalStateException();
      }
    }
  }

  public static ByteBuffer expandBuffer(ByteBuffer buffer, int growth) {
    final ByteBuffer expand = ByteBuffer.allocate(growth + buffer.position());
    buffer.flip();
    expand.put(buffer);
    return expand;
  }

  private void send() {
    if (toSend.position() == 0)
      return;
    toSend.flip();
    final ByteString data = ByteString.fromByteBuffer(toSend);
//    System.out.println("out: [" + data.mkString() + "]");
    getSender().tell(TcpMessage.write(data), self());
    toSend.clear();
  }
}
