package com.tbts.dao.eb.impl;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.spbsu.commons.filters.Filter;
import com.spbsu.commons.io.StreamTools;
import com.spbsu.commons.seq.CharSequenceReader;
import com.spbsu.commons.util.Pair;
import com.tbts.dao.MySQLOps;
import com.tbts.dao.eb.EventBus;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.Long.max;

/**
 * User: solar
 * Date: 11.11.15
 * Time: 14:28
 */
@SuppressWarnings("unused")
public class MySQLEventBus implements EventBus<String, CharSequence> {
  private final MySQLOps ops;
  private final Filter<String> connected;
  private final ReadWriteLock statesLock = new ReentrantReadWriteLock();
  private final Map<String, CombinedState<String, CharSequence>> states = new HashMap<>();
  private final String hostName;
  private final ObjectMapper mapper;
  private boolean sendEvts = true;

  public MySQLEventBus(String connectionUrl, Filter<String> connected) {
    System.out.println("Connecting event bus to db: " + connectionUrl);
    this.connected = connected;
    this.ops = new MySQLOps(connectionUrl);
    try {
      hostName = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
    mapper = new ObjectMapper();
    mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
    mapper.enable(SerializationFeature.WRITE_ENUMS_USING_INDEX);

    final Thread syncTh = new Thread("Event bus sync") {
      @Override
      public void run() {
        try {
          //noinspection InfiniteLoopStatement
          while (true) {
            statesLock.writeLock().lock();
            try {
              syncInner();
            } finally {
              statesLock.writeLock().unlock();
            }
            synchronized (MySQLEventBus.this) {
              MySQLEventBus.this.notifyAll();
            }
            synchronized (statesLock) {
              try {
                statesLock.wait(TimeUnit.SECONDS.toMillis(5));
              } catch (InterruptedException ignore) {
              }
            }
          }
        }
        finally {
          System.out.println("Exiting from sync thread");
        }
      }
    };
    syncTh.setDaemon(true);
    syncTh.start();
    final Thread gcTh = new Thread("Log GC") {
      @Override
      public void run() {
        //noinspection InfiniteLoopStatement
        while (true) {
          try {
            Thread.sleep(TimeUnit.MINUTES.toMillis(5));
            ops.createStatement("GC", "DELETE FROM tbts.Log WHERE id <= (SELECT min(age) FROM tbts.Nodes);").execute();
          }
          catch (SQLException e) {
            throw new RuntimeException(e);
          }
          catch (InterruptedException ignore) {
          }
        }
      }
    };
    gcTh.setDaemon(true);
    gcTh.start();
  }

  @Override
  public synchronized long sync() {
    synchronized (statesLock) {
      statesLock.notify();
    }

    try {
      this.wait();
    }
    catch (InterruptedException ignore) {
    }
    return age;
  }

  private long age = 0;
  public void syncInner() {
    statesLock.writeLock().lock();
    try {
      try {
        ops.conn().setAutoCommit(false);
        long newAge = age;
        newAge = max(receiveStates(), newAge);
        newAge = max(receiveUpdates(), newAge);
        newAge = max(sendUpdates(), newAge);
        sendStates(newAge);
        ops.createStatement("Delete node state", "DELETE FROM tbts.Nodes WHERE node='" + hostName + "';").execute();
        final PreparedStatement updateNodeState = ops.createStatement("Update node state", "INSERT tbts.Nodes SET node='" + hostName + "', heartbeat=CURRENT_TIMESTAMP(), age=?;");
        updateNodeState.setLong(1, newAge);
        updateNodeState.execute();
        ops.conn().commit();
        age = newAge;
        increment.clear();
        ops.conn().setAutoCommit(true);
      }
      catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
    finally {
      statesLock.writeLock().unlock();
    }
  }

  private long sendUpdates() {
    try {
      @SuppressWarnings("SqlDialectInspection")
      final PreparedStatement pushIncrement = ops.conn().prepareStatement("INSERT tbts.Log SET uid=?, data=?;", Statement.RETURN_GENERATED_KEYS);
      List<String> ids = new ArrayList<>();
      for (final Pair<String, CharSequence> pair : increment) {
        ids.add(pair.first);
        pushIncrement.setString(1, pair.first);
        pushIncrement.setCharacterStream(2, new CharSequenceReader(pair.second));
        pushIncrement.addBatch();
      }
      pushIncrement.executeBatch();
      final ResultSet generated = pushIncrement.getGeneratedKeys();
      long lastInserted = 0;
      int index = 0;
      while (generated.next()) {
        state(ids.get(index++)).advance(generated.getLong(1));
        lastInserted = max(generated.getLong(1), lastInserted);
      }
      return lastInserted;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private long receiveUpdates() {
    final PreparedStatement logUpdStmt = ops.createStatement("Receive log updates", "SELECT * FROM tbts.Log WHERE id > ?;");
    try {
      logUpdStmt.setLong(1, age);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
    long lastUpdate = age;
    try (final ResultSet rs = logUpdStmt.executeQuery()) {
      sendEvts = false;
      while (rs.next()) {
        final long time = rs.getLong(1);
        final String id = rs.getString(2);
        CombinedState<String, CharSequence> state = state(id);
        if (time > state.age())
          state.combine(StreamTools.readReader(rs.getCharacterStream(3)), time);
        lastUpdate = max(lastUpdate, time);
      }
      return lastUpdate;
    }
    catch (SQLException | IOException e) {
      throw new RuntimeException(e);
    }
    finally {
      sendEvts = true;
    }
  }

  private void sendStates(long newAge) {
    final Set<String> known = new HashSet<>();
    try (final ResultSet rs = ops.createStatement("List known states", "SELECT id FROM tbts.States;").executeQuery()) {
      while (rs.next()) {
        known.add(rs.getString(1));
      }
    }
    catch (SQLException e) {
      throw new RuntimeException(e);
    }
    { // outgoing
      try {
        final PreparedStatement stateInsStmt = ops.createStatement("Send new states insert", "INSERT tbts.States SET id=?, age=?, data=?, type=?;");
        final PreparedStatement stateUpdStmt = ops.createStatement("Send new states update", "UPDATE tbts.States SET age=?, data=? WHERE id=?;");
        visitStates((id, state) -> {
          try {
            if (isMaster(id) && state.age() > age) {
              if (known.contains(id)) {
                stateUpdStmt.setLong(1, state.age());
                stateUpdStmt.setCharacterStream(2, new CharSequenceReader(mapper.writeValueAsString(state)));
                stateUpdStmt.setString(3, id);
                stateUpdStmt.addBatch();
              }
              else {
                stateInsStmt.setString(1, id);
                stateInsStmt.setLong(2, state.age());
                stateInsStmt.setCharacterStream(3, new CharSequenceReader(mapper.writeValueAsString(state)));
                stateInsStmt.setString(4, state.getClass().getName());
                stateInsStmt.addBatch();
              }
            }
          }
          catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException(e);
          }
          return false;
        });
        stateUpdStmt.executeBatch();
        stateInsStmt.executeBatch();
      }
      catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private long receiveStates() {
    final PreparedStatement stateUpdStmt = ops.createStatement("Receive new states", "SELECT * FROM tbts.States WHERE age >= ?;");
    try {
      stateUpdStmt.setLong(1, age);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
    try (final ResultSet rs = stateUpdStmt.executeQuery()) {
      long maxAge = 0;
      while (rs.next()) {
        final String id = rs.getString(1);
        maxAge = max(rs.getLong(2), maxAge);
        if (states.containsKey(id))
          continue;
        final CharSequence data = StreamTools.readReader(rs.getCharacterStream(3));
        final String type = rs.getString(4);
        //noinspection unchecked
        final CombinedState<String, CharSequence> value = (CombinedState<String,CharSequence>) mapper.readValue(new CharSequenceReader(data), Class.forName(type));
        value.bus(this);
        states.put(id, value);
      }
      return maxAge;
    } catch (SQLException | IOException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean isMaster(String uid) {
    return connected.accept(uid);
  }

  @Override
  public <S extends CombinedState<String, CharSequence>> S state(String id) {
    statesLock.readLock().lock();
    try {
      //noinspection unchecked
      return (S) states.get(id);
    }
    finally {
      statesLock.readLock().unlock();
    }
  }

  @Override
  public void register(String id, CombinedState<String, CharSequence> state) {
    long age = sync();
    statesLock.writeLock().lock();
    try {
      state.bus(this);
      state.advance(age);
      states.put(id, state);
    }
    finally {
      statesLock.writeLock().unlock();
    }
  }

  @Override
  public void visitStates(StateVisitor<String, CharSequence> visitor) {
    statesLock.readLock().lock();
    try {
      for (final Map.Entry<String, CombinedState<String, CharSequence>> entry : states.entrySet()) {
        visitor.accept(entry.getKey(), entry.getValue());
      }
    }
    finally {
      statesLock.readLock().unlock();
    }
  }

  private final List<Pair<String, CharSequence>> increment = new ArrayList<>();
  @Override
  public void log(String id, CharSequence evt) {
    if (!sendEvts)
      return;
    statesLock.writeLock().lock();
    try {
      increment.add(Pair.create(id, evt));
    }
    finally {
      statesLock.writeLock().unlock();
    }
  }
}
