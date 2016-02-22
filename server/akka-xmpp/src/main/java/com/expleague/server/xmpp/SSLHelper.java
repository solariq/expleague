package com.expleague.server.xmpp;

import akka.util.ByteString;
import com.expleague.server.xmpp.phase.SSLHandshake;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * User: solar
 * Date: 21.12.15
 * Time: 22:01
 */
public class SSLHelper {
  public SSLHelper(SSLEngine sslEngine) {
    this.sslEngine = sslEngine;
  }
  private SSLEngine sslEngine;

  private ByteBuffer inDst = ByteBuffer.allocate(4 * 4096);
  private ByteBuffer inSrc = ByteBuffer.allocate(4 * 4096);
  public void decrypt(ByteString msgIn, Consumer<ByteString> consumer) {
    inDst = sslPipe(msgIn, consumer, inSrc, inDst, true);
  }

  private ByteBuffer outDst = ByteBuffer.allocate(4 * 4096);
  private ByteBuffer outSrc = ByteBuffer.allocate(4 * 4096);
  public void encrypt(ByteString data, Consumer<ByteString> consumer) throws SSLException {
    outDst = sslPipe(data, consumer, outSrc, outDst, false);
  }

  private ByteBuffer sslPipe(ByteString msgIn, Consumer<ByteString> consumer, ByteBuffer src, ByteBuffer dst, boolean incoming) {
    try {
      final ByteBuffer inBuffer = msgIn.asByteBuffer();
      while (inBuffer.remaining() > 0) {
        if (inBuffer.remaining() > src.remaining()) {
          final ByteBuffer slice = inBuffer.slice();
          slice.limit(src.remaining());
          inBuffer.position(slice.position());
          src.put(slice);
        }
        else src.put(inBuffer);
        do {
          src.flip();
          final SSLEngineResult r = incoming ? sslEngine.unwrap(src, dst) : sslEngine.wrap(src, dst);
          switch (r.getStatus()) {
            case BUFFER_OVERFLOW: {
              // Could attempt to drain the dst buffer of any already obtained
              // data, but we'll just increase it to the size needed.
              dst = SSLHandshake.expandBuffer(dst, sslEngine.getSession().getApplicationBufferSize());
              src.compact();
              continue;
            }
            case BUFFER_UNDERFLOW: {
              int netSize = sslEngine.getSession().getPacketBufferSize();
              // Resize buffer if needed.
              if (netSize > dst.capacity()) {
                dst = SSLHandshake.expandBuffer(dst, netSize);
              }
              // Obtain more inbound network data for inSrc,
              // then retry the operation.
              return dst;
              // other cases: CLOSED, OK.
            }
          }
          dst.flip();
          src.compact();
          if (dst.limit() > 0) {
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
    return dst;
  }
}
