package com.expleague.server.xmpp;

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

  public void decrypt(ByteString msgIn, Consumer<ByteString> consumer) {
    in.process(msgIn, consumer);
  }

  public void encrypt(ByteString data, Consumer<ByteString> consumer) throws SSLException {
    out.process(data, consumer);
  }

  private class SSLDirection {
    private ByteBuffer dst;
    private ByteBuffer src;
    private final boolean incoming;

    private SSLDirection(boolean incoming) {
      this.incoming = incoming;
      if (incoming) {
        dst = ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize());
        src = ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize());
      }
      else {
        src = ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize());
        dst = ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize());
      }
    }

    private void process(ByteString msgIn, Consumer<ByteString> consumer) {
      try {
        final ByteBuffer inBuffer = msgIn.asByteBuffer();
        while (inBuffer.remaining() > 0) {
          if (inBuffer.remaining() > src.remaining()) {
            final ByteBuffer slice = inBuffer.slice();
            slice.limit(src.remaining());
            inBuffer.position(inBuffer.position() + slice.limit());
            src.put(slice);
          }
          else src.put(inBuffer);
          while(true) {
            src.flip();
            final SSLEngineResult r;
            r = incoming ? sslEngine.unwrap(src, dst) : sslEngine.wrap(src, dst);
            switch (r.getStatus()) {
              case BUFFER_OVERFLOW: {
                sendChunk(consumer);
                src.compact();
                continue;
              }
              case BUFFER_UNDERFLOW: {
                return;
              }
            }
            dst.flip();
            src.compact();
            sendChunk(consumer);
            break;
          }
        }
      }
      catch (SSLException e) {
        throw new RuntimeException(e);
      }
    }

    private void sendChunk(Consumer<ByteString> consumer) {
      if (dst.limit() == 0)
        return;
//      log.finest(dst.limit() + " bytes " + (incoming ? "received" : "sent"));
      consumer.accept(ByteString.fromByteBuffer(dst));
      dst.clear();
    }
  }
}
