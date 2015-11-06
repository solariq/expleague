package com.tbts.model.handlers;

import com.spbsu.commons.filters.Filter;
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
  public static final long EXPERT_ACCEPT_INVITATION_TIMEOUT = TimeUnit.MINUTES.toMillis(5);

  public static synchronized ExpertManager instance() {
    return EXPERT_MANAGER;
  }

  private Map<String, Expert> experts() {
    return DAO.instance().experts();
  }

  public void nextAvailable(Room room, Filter<Expert> action) {
    while (true) {
      for (final Expert expert : experts().values()) {
        if (expert.state() == Expert.State.READY && room.relevant(expert))
          if (action.accept(expert))
            return;
      }
      synchronized (this) {
        try {
          wait();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
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
    return DAO.instance().expert(jid);
  }

  @Override
  public synchronized void invoke(Expert e) {
    if (e.state() == Expert.State.READY)
      notifyAll();

    super.invoke(e);
  }

  private final Set<Room> challenged = new HashSet<>();
  public void challenge(final Room room) {
    if (challenged.contains(room))
      return;
    challenged.add(room);
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
      if (!reserved.contains(expert))
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

    System.out.println("Starting challenge for room: " + room.id());
    log.fine("Starting challenge for room: " + room.id());

    while (room.state() == Room.State.DEPLOYED) {
      while (!room.quorum(reserved)) {
        nextAvailable(room, expert -> {
          expert.addListener(challenge);
          reserved.add(expert);
          if (!expert.reserve(room)) {
            expert.removeListener(challenge);
            reserved.remove(expert);
            return false;
          }
          return true;
        });
      }

      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      while (!steady.isEmpty() && !winner.filled()) {
        final Iterator<Expert> iterator = steady.iterator();
        final Expert next = iterator.next();
        iterator.remove();
        next.invite();
        new Timer(true).schedule(new TimerTask() {
          @Override
          public void run() {
            if (next.state() == Expert.State.INVITE && next.active() == room)
              next.free();
          }
        }, room.invitationTimeout());
      }
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (winner) {
        if (winner.filled() && (winner.getValue().state() == Expert.State.GO || winner.getValue().state() == Expert.State.READY))
          break;
        if (room.quorum(reserved)) {
          try {
            winner.wait(0);
          } catch (InterruptedException ignore) {
          }
        }
      }
    }
    reserved.stream().filter(expert -> expert.state() == Expert.State.STEADY).forEach(Expert::free);
    System.out.println("Challenge for room " + room.id() + " finished. Winner: " + winner.getValue().id());
    log.fine("Challenge for room " + room.id() + " finished. Winner: " + winner.getValue().id());
    challenged.remove(room);
    room.enter(winner.getValue());
  }
}
