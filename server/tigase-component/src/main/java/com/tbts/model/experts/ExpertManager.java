package com.tbts.model.experts;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.spbsu.commons.util.Holder;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import com.tbts.model.impl.ExpertImpl;
import tigase.xmpp.BareJID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 24.09.15
 * Time: 23:14
 */
public class ExpertManager extends WeakListenerHolderImpl<Expert> implements Action<Expert> {
  private static final Logger log = Logger.getLogger(ExpertManager.class.getName());
  public static final ExpertManager EXPERT_MANAGER = new ExpertManager();

  public static synchronized ExpertManager instance() {
    return EXPERT_MANAGER;
  }

  private final Map<BareJID, Expert> experts = new HashMap<>();
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")

  public synchronized Expert nextAvailable(Room req) {
    while (true) {
      for (final Expert expert : experts.values()) {
        if (expert.state() == Expert.State.READY)
          return expert;
      }
      try {
        wait();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
  public synchronized Expert register(BareJID expert) {
    return register(new ExpertImpl(expert));
  }

  public synchronized Expert register(final Expert expert) {
    experts.put(expert.id(), expert);
    expert.addListener(this);
    invoke(expert);
    this.notifyAll();
    return expert;
  }

  public synchronized Expert get(BareJID jid) {
    return experts.get(jid);
  }

  public synchronized int count() {
    int result = 0;
    for (Expert expert : experts.values()) {
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

  final Map<Room, Action<Expert>> challenges = new ConcurrentHashMap<>();
  public synchronized void challenge(final Room room) {
    final Thread thread = new Thread(() -> challengeImpl(room));
    thread.setDaemon(true);
    thread.setName("Challenge thread");
    thread.start();
  }

  private void challengeImpl(Room room) {
    final Holder<Expert> winner = new Holder<>();
    final Set<Expert> reserved = Collections.synchronizedSet(new HashSet<>());
    final Set<Expert> steady = Collections.synchronizedSet(new HashSet<>());
    final Set<Expert> invited = new HashSet<>();
    final Action<Expert> challenge = expert -> {
      if (!reserved.contains(expert) || room.state() != Room.State.DEPLOYED)
        return;
      if (expert.active() != room || (
              expert.state() != Expert.State.CHECK &&
              expert.state() != Expert.State.STEADY &&
              expert.state() != Expert.State.INVITE &&
              expert.state() != Expert.State.GO)) {
        reserved.remove(expert);
        if (winner.getValue() == expert)
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
    challenges.put(room, challenge);

    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (winner) {
      while (true) {
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

          if (invited.contains(next))
            continue;
          winner.setValue(next);
          invited.add(next);
          next.invite();
        }
        if (winner.filled() && (winner.getValue().state() == Expert.State.GO || winner.getValue().state() == Expert.State.READY))
          break;
        try {
          winner.wait(0);
        }
        catch (InterruptedException ignore) {}
      }
    }
    for (final Expert expert : reserved) {
      if (expert.state() == Expert.State.STEADY)
        expert.free();
    }
    room.enterExpert(winner.getValue());
    log.fine("Challenge for room " + room.id() + " finished. Winner: " + winner.getValue().id());
    challenges.remove(room);
  }
}
