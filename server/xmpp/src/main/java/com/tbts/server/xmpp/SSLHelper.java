package com.tbts.server.xmpp;

import akka.util.ByteString;
import com.tbts.server.xmpp.phase.SSLHandshake;

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

  private ByteBuffer inDst = ByteBuffer.allocate(4096);
  private ByteBuffer inSrc = ByteBuffer.allocate(4096);
  public void decrypt(ByteString msgIn, Consumer<ByteString> consumer) {
    try {
      inSrc.put(msgIn.asByteBuffer());
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
            decrypt(msgIn, consumer);
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
          consumer.accept(ByteString.fromByteBuffer(inDst));
          inDst.clear();
        }
        inSrc.compact();
      }
    }
    catch (SSLException e) {
      throw new RuntimeException(e);
    }
  }


  private ByteBuffer outDst = ByteBuffer.allocate(4096);
  private ByteBuffer outSrc = ByteBuffer.allocate(4096);
  public void encrypt(ByteString data, Consumer<ByteString> consumer) throws SSLException {
    if (outSrc.remaining() < data.length())
      outSrc = SSLHandshake.expandBuffer(outSrc, data.length());
    data.copyToBuffer(outSrc);
OUTER:
    while (outSrc.position() > 0) {
      outSrc.flip();
      final SSLEngineResult result = sslEngine.wrap(outSrc, outDst);
      outSrc.compact();
      switch (result.getStatus()) {
        case BUFFER_OVERFLOW:
          outDst = SSLHandshake.expandBuffer(outDst, sslEngine.getSession().getPacketBufferSize());
          break;
        case BUFFER_UNDERFLOW:
          throw new RuntimeException("Strange happened!");
        default:
          outDst.flip();
          if (outDst.limit() == 0)
            break OUTER;
          consumer.accept(ByteString.fromByteBuffer(outDst));
          outDst.clear();
      }
    }
  }
}
