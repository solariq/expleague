package com.tbts.model.experts;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import com.tbts.model.impl.ExpertImpl;
import tigase.xmpp.BareJID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: solar
 * Date: 24.09.15
 * Time: 23:14
 */
public class ExpertManager extends WeakListenerHolderImpl<Expert> implements Action<Expert> {
  public static final ExpertManager EXPERT_MANAGER = new ExpertManager();

  public static synchronized ExpertManager instance() {
    return EXPERT_MANAGER;
  }

  private final Map<BareJID, Expert> experts = new HashMap<>();
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")

  public synchronized Expert nextAvailable(Room req) {
    final List<Expert> slice = new ArrayList<>(experts.size());
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
  public void invoke(Expert e) {
    super.invoke(e);
  }

  final Map<Room, Action<Expert>> challenges = new ConcurrentHashMap<>();
  public synchronized void challenge(final Room room) {
    final Thread thread = new Thread(() -> challengeImpl(room));
    thread.setDaemon(true);
    thread.setName("Challenge thread");
  }

  private void challengeImpl(Room room) {
    final Action<Expert> challenge = expert -> {
      if (!room.equals(expert.active()) || expert.state() != Expert.State.STEADY)
        return;

      if (room.state() == Room.State.DEPLOYED) {
        room.enterExpert(expert);
        challenges.remove(room);
      }
      else expert.free();
    };
    challenges.put(room, challenge);
    final List<Expert> challenged = new ArrayList<>();
    while (challenges.containsKey(room)) {
      final Expert next = nextAvailable(room);
      next.addListener(challenge);
      next.reserve(room);
      challenged.add(next);
    }
  }
}
