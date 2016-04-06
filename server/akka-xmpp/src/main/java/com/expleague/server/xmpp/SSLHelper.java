package com.expleague.server.xmpp;

import akka.actor.ActorRef;
import akka.util.ByteString;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 21.12.15
 * Time: 22:01
 */
public class SSLHelper {
  private static final Logger log = Logger.getLogger(SSLHelper.class.getName());
  private final SSLEngine sslEngine;
  private final SSLDirection in;
  private final SSLDirection out;

  public SSLHelper(SSLEngine sslEngine) {
    this.sslEngine = sslEngine;
    in = new SSLDirection(true);
    out = new SSLDirection(false);
  }

  public static ByteBuffer expandBuffer(ByteBuffer buffer, int growth) {
    final ByteBuffer expand = ByteBuffer.allocate(growth + buffer.position());
    buffer.flip();
    expand.put(buffer);
    return expand;
  }

  public void decrypt(ByteString msgIn, Consumer<ByteString> consumer) {
    in.process(msgIn, consumer);
  }

  public void encrypt(ByteString data, Consumer<ByteString> consumer, ActorRef self) throws SSLException {
    out.process(data, consumer);
  }

  private class SSLDirection {
    private ByteBuffer dst = ByteBuffer.allocate(4 * 4096);
    private ByteBuffer src = ByteBuffer.allocate(4 * 4096);
    private final boolean incoming;
    private final int netSize;

    private SSLDirection(boolean incoming) {
      this.incoming = incoming;
      netSize = sslEngine.getSession().getApplicationBufferSize();
    }

    private void process(ByteString msgIn, Consumer<ByteString> consumer) {
      try {
        final ByteBuffer inBuffer = msgIn.asByteBuffer();
        while (inBuffer.remaining() > 0) {
          if (src.remaining() < netSize / 2) {
            src = expandBuffer(src, netSize);
          }
          if (inBuffer.remaining() > src.remaining()) {
            final ByteBuffer slice = inBuffer.slice();
            slice.limit(src.remaining());
            inBuffer.position(slice.limit());
            src.put(slice);
          }
          else src.put(inBuffer);
          do {
            src.flip();
            final SSLEngineResult r;
            r = incoming ? sslEngine.unwrap(src, dst) : sslEngine.wrap(src, dst);
            switch (r.getStatus()) {
              case BUFFER_OVERFLOW: {
                // Could attempt to drain the dst buffer of any already obtained
                // data, but we'll just increase it to the size needed.
                dst = expandBuffer(dst, netSize);
                src.compact();
                continue;
              }
              case BUFFER_UNDERFLOW: {
                // Resize buffer if needed.
                if (netSize > dst.capacity()) {
                  dst = expandBuffer(dst, netSize);
                }
                // Obtain more inbound network data for inSrc,
                // then retry the operation.
                return;
                // other cases: CLOSED, OK.
              }
            }
            dst.flip();
            src.compact();
            if (dst.limit() > 0) {
              log.finest(dst.limit() + " bytes " + (incoming ? "received" : "sent"));
              consumer.accept(ByteString.fromByteBuffer(dst));
              dst.clear();
            }
            else if (r.bytesConsumed() == 0)
              break;
            if (src.position() > 0)
              break;
          }
          while (true);
        }
      }
      catch (SSLException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
