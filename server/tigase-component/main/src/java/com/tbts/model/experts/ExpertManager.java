package com.tbts.model.experts;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import com.tbts.model.impl.ExpertImpl;
import tigase.xmpp.BareJID;

import java.util.*;

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

  public synchronized Iterator<Expert> available(Room req) {
    final List<Expert> slice = new ArrayList<>(experts.size());
    for (final Expert expert : experts.values()) {
      if (expert.state() == Expert.State.READY)
        slice.add(expert);
    }
    return slice.iterator();
  }
  public synchronized Expert register(BareJID expert) {
    return register(new ExpertImpl(expert));
  }

  public synchronized Expert register(final Expert expert) {
    experts.put(expert.id(), expert);
    expert.addListener(this);
    invoke(expert);
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

  final Map<Room, Action<Expert>> challenges = new HashMap<>();
  public synchronized void challenge(final Room room) {
    final Iterator<Expert> available = available(room);
    final Action<Expert> challenge = expert -> {
      if (!room.equals(expert.active()))
        return;

      if (expert.state() == Expert.State.STEADY && room.state() == Room.State.DEPLOYED) {
        room.enterExpert(expert);
        challenges.remove(room);
      }
      else expert.free();
    };
    challenges.put(room, challenge);
    while (available.hasNext()) {
      final Expert next = available.next();
      next.addListener(challenge);
      next.reserve(room);
    }
  }
}
