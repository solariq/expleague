package com.expleague.expert.xmpp.events;

import com.expleague.expert.xmpp.ExpertEvent;
import com.expleague.expert.xmpp.ExpertTask;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.stanza.Message;

/**
 * Experts League
 * Created by solar on 11/02/16.
 */
public class ExpertTaskEvent extends ExpertEvent {
  private final ExpertTask task;

  public ExpertTaskEvent(Item source, ExpertTask task) {
    super(source);
    this.task = task;
  }

  public ExpertTask task() {
    return task;
  }
}
