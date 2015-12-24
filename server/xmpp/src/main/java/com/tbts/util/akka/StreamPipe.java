package com.tbts.util.akka;

import akka.util.ByteString;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * User: solar
 * Date: 24.12.15
 * Time: 23:37
 */
public class StreamPipe extends UntypedActorAdapter {
  private PipedOutputStream pipeIn;

  public StreamPipe(PipedInputStream pis) throws IOException {
    pipeIn = new PipedOutputStream(pis);
  }

  public void invoke(ByteString string) throws IOException {
    byte[] buffer = new byte[string.length()];
    string.copyToArray(buffer);
    pipeIn.write(buffer);
  }

  public void invoke(Close close) throws IOException {
    pipeIn.close();
    context().stop(self());
  }

  public void invoke(Open open) {
    sender().tell(open, self());
  }

  public static final class Close { }

  public static final class Open { }
}
