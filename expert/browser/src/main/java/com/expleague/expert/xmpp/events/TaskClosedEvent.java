package com.expleague.expert.xmpp.events;

import com.expleague.expert.xmpp.ExpertEvent;
import com.expleague.expert.xmpp.ExpertTask;
import com.expleague.model.Operations;
import com.expleague.xmpp.Item;

/**
 * Experts League
 * Created by solar on 11/02/16.
 */
public class TaskClosedEvent extends TaskSuspendedEvent {
  public TaskClosedEvent(Item command, ExpertTask task) {
    super(command, task);
  }
}
