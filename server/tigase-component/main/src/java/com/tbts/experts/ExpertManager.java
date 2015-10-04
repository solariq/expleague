package com.tbts.experts;

import com.tbts.model.Expert;
import com.tbts.model.Room;
import tigase.xmpp.BareJID;

import java.util.*;

/**
 * User: solar
 * Date: 24.09.15
 * Time: 23:14
 */
public class ExpertManager {

  public static final ExpertManager EXPERT_MANAGER = new ExpertManager();

  public static synchronized ExpertManager instance() {
    return EXPERT_MANAGER;
  }

  private final Map<BareJID, Expert> experts = new HashMap<>();

  public synchronized Iterator<Expert> available(Room req) {
    final List<Expert> slice = new ArrayList<>(experts.size());
    for (Expert expert : experts.values()) {
      if (expert.state() == Expert.State.READY)
        slice.add(expert);
    }
    return slice.iterator();
  }
  public synchronized void register(Expert expert) {
    experts.put(expert.id(), expert);
  }

  public synchronized Expert get(BareJID jid) {
    return experts.get(jid);
  }
}
