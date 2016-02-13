package com.expleague.expert.xmpp.events;

import com.expleague.expert.xmpp.ExpertTask;
import com.expleague.xmpp.Item;

/**
 * Experts League
 * Created by solar on 11/02/16.
 */
public class TaskStartedEvent extends ExpertTaskEvent {
  public TaskStartedEvent(Item source, ExpertTask task) {
    super(source, task);
  }
}
