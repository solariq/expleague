package com.expleague.server.xmpp;

import akka.actor.Terminated;
import akka.dispatch.Futures;
import akka.japi.JavaPartialFunction;
import akka.persistence.AtomicWrite;
import akka.persistence.PersistentImpl;
import akka.persistence.PersistentRepr;
import akka.persistence.journal.japi.AsyncWriteJournal;
import com.expleague.xmpp.Item;
import com.spbsu.commons.io.StreamTools;
import com.spbsu.commons.util.Holder;
import com.spbsu.commons.util.cache.CacheStrategy;
import com.spbsu.commons.util.cache.impl.FixedSizeCache;
import com.typesafe.config.Config;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteBatch;
import org.iq80.leveldb.impl.DbImpl;
import scala.Function1;
import scala.PartialFunction;
import scala.collection.JavaConversions;
import scala.collection.immutable.Seq;
import scala.concurrent.Future;
import scala.runtime.AbstractPartialFunction;
import scala.runtime.BoxedUnit;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 01/03/16.
 */
public class XMPPLevelDBJournal extends AsyncWriteJournal {
  private static final Logger log = Logger.getLogger(XMPPLevelDBJournal.class.getName());
  private final DB db;

  public XMPPLevelDBJournal(Config config) {
    try {
      db = new DbImpl(
          new Options().createIfMissing(true),
          new File(config.getString("root"))
      );
    }
    catch (IOException e) {
      log.log(Level.SEVERE, "Unable to create messages journal!", e);
      throw new RuntimeException(e);
    }
  }

  public Future<Iterable<Optional<Exception>>> doAsyncWriteMessages(Iterable<AtomicWrite> messages){
    try {
      final List<Optional<Exception>> result = new ArrayList<>();
      for (AtomicWrite message : messages) {
        final WriteBatch batch = db.createWriteBatch();
        final Seq<PersistentRepr> payload = message.payload();
        Exception error = null;
        for (final PersistentRepr writeItem : JavaConversions.asJavaIterable(payload)) {
          final ByteBuffer key = key(writeItem.persistenceId(), writeItem.sequenceNr());
          if (!writeItem.deleted()) {
            final Object item = writeItem.payload();
            if (item instanceof Item) {
//              System.out.println("Writing: " + item + " -> " + writeItem.sequenceNr());
              final String str = ((Item) item).xmlString();
              batch.put(key.array(), str.getBytes(StreamTools.UTF));
            }
            else {
              //noinspection ThrowableInstanceNeverThrown
              error = new IllegalArgumentException("Unable to serialize not an Item object: " + item);
              break;
            }
          }
          else batch.delete(key.array());
        }
        batch.close();
        if (error == null) {
          result.add(Optional.empty());
          db.write(batch);
        }
        else result.add(Optional.of(error));
      }
      return Futures.successful(result);
    } catch (Exception e) {
      return Futures.failed(e);
    }
  }

  public Future<Void> doAsyncDeleteMessagesTo(String persistenceId, long toSequenceNr) {
    return Futures.future(() -> {
      final WriteBatch writeBatch = db.createWriteBatch();
      visitKeyRange(persistenceId, 0, toSequenceNr, (id, seq, key, data) -> writeBatch.delete(key));
      db.write(writeBatch);
      return null;
    }, context().dispatcher());
  }

  public Future<Void> doAsyncReplayMessages(String persistenceId, long fromSequenceNr,
                                     long toSequenceNr, long max, Consumer<PersistentRepr> replayCallback) {
    return Futures.future(() -> {
      visitKeyRange(persistenceId, 0, toSequenceNr, (id, seq, key, data) -> {
        final Item item = Item.create(StreamTools.UTF.decode(ByteBuffer.wrap(data)));
        if (item != null)
          replayCallback.accept(new PersistentImpl(item, seq, id, "", false, self(), ""));
      });
      return null;
    }, context().dispatcher());
  }

  public Future<Long> doAsyncReadHighestSequenceNr(String persistenceId, long fromSequenceNr) {
    return Futures.future(() -> {
      final Holder<Long> result = new Holder<>(0L);
      visitKeyRange(persistenceId, fromSequenceNr, Long.MAX_VALUE, (id, seq, key, data) -> {
        if (result.getValue() < seq)
          result.setValue(seq);
      });
      return result.getValue();
    }, context().dispatcher());
  }

  @Override
  public void postStop() throws Exception {
    db.close();
    super.postStop();
  }

  private interface EntryVisitor {
    void accept(String id, long seq, byte[] key,  byte[] data);
  }

  private void visitKeyRange(String persistenceId, long start, long end, EntryVisitor visitor) throws IOException {
    try (final DBIterator iterator = db.iterator()) {
      final ByteBuffer key = key(persistenceId, start);
      iterator.seek(key.array());
      while (iterator.hasNext()) {
        final Map.Entry<byte[], byte[]> next = iterator.next();
        final ByteBuffer currentKey = ByteBuffer.wrap(next.getKey());
        if (currentKey.remaining() < key.remaining())
          break;
        currentKey.limit(key.remaining());
        if (currentKey.compareTo(key) != 0)
          break;
        if (currentKey.capacity() >= currentKey.limit() + 9) {
          currentKey.limit(currentKey.capacity());
          currentKey.position(key.remaining() + 1);
          final long index = currentKey.getLong();
          visitor.accept(persistenceId, index, next.getKey(), next.getValue());
          if (index >= end)
            break;
        }
        else visitor.accept(persistenceId, 0, next.getKey(), next.getValue());
      }
    }
  }

  final FixedSizeCache<String, byte[]> keysCache = new FixedSizeCache<>(10000, CacheStrategy.Type.LRU);
  private ByteBuffer key(String id, long num) {
    final byte[] idBytes = keysCache.get(id, argument -> id.getBytes(StreamTools.UTF));
    final ByteBuffer key;
    if (num > 0) {
      key = ByteBuffer.allocate(idBytes.length + 9);
      key.put(idBytes);
      key.put((byte) 0);
      key.putLong(num);
      key.flip();
    }
    else key = ByteBuffer.wrap(idBytes);
    return key;
  }
}
