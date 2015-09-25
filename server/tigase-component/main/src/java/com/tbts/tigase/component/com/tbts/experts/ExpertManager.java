package com.tbts.tigase.component.com.tbts.experts;

import tigase.xmpp.BareJID;

import java.util.HashMap;
import java.util.Map;

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

  private final Map<BareJID, Status> experts = new HashMap<>();

  public synchronized BareJID nextAvailable() {
    for (Map.Entry<BareJID, Status> entry : experts.entrySet()) {
      if (entry.getValue() == Status.AVAILABLE) {
        return entry.getKey();
      }
    }
    return null;
  }

  public synchronized void changeStatus(BareJID expert, Status status) {
    experts.put(expert, status);
  }

  public enum Status {
    AVAILABLE,
    OFFLINE,
    BUSY
  }
}
