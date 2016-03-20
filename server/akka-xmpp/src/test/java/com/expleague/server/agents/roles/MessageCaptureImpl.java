package com.expleague.server.agents.roles;

import akka.actor.ActorRef;
import com.expleague.util.akka.MessageCapture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author vpdelta
 */
public class MessageCaptureImpl implements MessageCapture {
  private final List<MessageCaptureRecord> records = new ArrayList<>();

  @Override
  public synchronized void capture(final ActorRef from, final ActorRef to, final Object message) {
    records.add(new MessageCaptureRecord(from, to, message));
  }

  public synchronized void reset() {
    records.clear();
  }

  public void expect(final String message, final long maxTimeoutMs, Predicate<List<MessageCaptureRecord>> condition) throws Exception {
    expectAndGet(message, maxTimeoutMs, messageCaptureRecords -> condition.test(messageCaptureRecords) ? messageCaptureRecords : Collections.emptyList());
  }

  public <T> List<T> expectAndGet(final String message, final long maxTimeoutMs, Function<List<MessageCaptureRecord>, List<T>> extractor) throws Exception {
    final long startMs = System.currentTimeMillis();
    while (true) {
      final ArrayList<MessageCaptureRecord> copy;
      synchronized (this) {
        copy = new ArrayList<>(records);
      }

      final List<T> result = extractor.apply(copy);
      if (result != null && !result.isEmpty()) {
        return result;
      }

      final long elapsedMs = System.currentTimeMillis() - startMs;
      if (elapsedMs >= maxTimeoutMs) {
        final List<String> trace;
        synchronized (this) {
          trace = records.stream().map(r -> r.toString() + "\n").collect(Collectors.toList());
        }
        throw new IllegalStateException(message + " expectation failed after " + elapsedMs + " ms\nRecords:\n" + trace);
      }
      Thread.sleep(10);
    }
  }
}
