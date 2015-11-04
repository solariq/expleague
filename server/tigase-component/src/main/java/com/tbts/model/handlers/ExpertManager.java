package com.tbts.model.handlers;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.spbsu.commons.util.Holder;
import com.tbts.model.Expert;
import com.tbts.model.Room;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 24.09.15
 * Time: 23:14
 */
public class ExpertManager extends WeakListenerHolderImpl<Expert> implements Action<Expert> {
  private static final Logger log = Logger.getLogger(ExpertManager.class.getName());
  public static final ExpertManager EXPERT_MANAGER = new ExpertManager();
  public static final long EXPERT_ACCEPT_INVITATION_TIMEOUT = TimeUnit.SECONDS.toMillis(10);

  public static synchronized ExpertManager instance() {
    return EXPERT_MANAGER;
  }

  private Map<String, Expert> experts() {
    return DAO.instance().experts();
  }

  public synchronized Expert nextAvailable(Room room) {
    while (true) {
      for (final Expert expert : experts().values()) {
        if (expert.state() == Expert.State.READY && room.relevant(expert))
          return expert;
      }
      try {
        wait();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public synchronized Expert register(String id) {
    if (!id.contains("@") || id.contains("@muc."))
      return null;

    Expert expert = DAO.instance().expert(id);
    if (expert == null) {
      expert = DAO.instance().createExpert(id);
      invoke(expert);
    }
    return expert;
  }

  public synchronized Expert get(String jid) {
    return experts().get(jid);
  }

  public synchronized int count() {
    int result = 0;
    for (Expert expert : experts().values()) {
      if (expert.state() != Expert.State.AWAY)
        result++;
    }
    return result;
  }

  @Override
  public synchronized void invoke(Expert e) {
    if (e.state() == Expert.State.READY)
      notifyAll();

    super.invoke(e);
  }

  public synchronized void challenge(final Room room) {
    final Thread thread = new Thread(() -> challengeImpl(room));
    thread.setDaemon(true);
    thread.setName("Challenge thread for " + room.id());
    thread.start();
  }

  private void challengeImpl(Room room) {
    final Holder<Expert> winner = new Holder<>();
    final Set<Expert> reserved = Collections.synchronizedSet(new HashSet<>());
    final Set<Expert> steady = Collections.synchronizedSet(new HashSet<>());
    final Action<Expert> challenge = expert -> {
      if (!reserved.contains(expert) || room.state() != Room.State.DEPLOYED)
        return;
      if (expert.active() != room || (
              expert.state() != Expert.State.CHECK &&
              expert.state() != Expert.State.STEADY &&
              expert.state() != Expert.State.INVITE &&
              expert.state() != Expert.State.GO)) {
        reserved.remove(expert);
        if (winner.getValue() == expert && room.state() != Room.State.COMPLETE)
          winner.setValue(null);
        steady.remove(expert);
      }
      else if (expert.state() == Expert.State.STEADY) {
        steady.add(expert);
      }
      else if (expert.state() == Expert.State.GO) {
        winner.setValue(expert);
      }
      synchronized (winner) {
        winner.notifyAll();
      }
    };

    log.fine("Starting challenge for room: " + room.id());

    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (winner) {
      while (room.state() == Room.State.DEPLOYED) {
        while (!room.quorum(reserved)) {
          final Expert next = nextAvailable(room);
          next.addListener(challenge);
          reserved.add(next);
          next.reserve(room);
        }

        while (!steady.isEmpty() && !winner.filled()) {
          final Iterator<Expert> iterator = steady.iterator();
          final Expert next = iterator.next();
          iterator.remove();

          room.invite(next);
          new Timer(true).schedule(new TimerTask() {
            @Override
            public void run() {
              if (next.state() == Expert.State.INVITE && next.active() == room)
                next.free();
            }
          }, room.invitationTimeout());
        }
        if (winner.filled() && (winner.getValue().state() == Expert.State.GO || winner.getValue().state() == Expert.State.READY))
          break;
        if (room.quorum(reserved)) {
          try {
            winner.wait(0);
          }
          catch (InterruptedException ignore) {}
        }
      }
    }
    for (final Expert expert : reserved) {
      if (expert.state() == Expert.State.STEADY)
        expert.free();
    }
    log.fine("Challenge for room " + room.id() + " finished. Winner: " + winner.getValue().id());
    room.enter(winner.getValue());
  }
}
