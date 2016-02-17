package com.expleague.expert.xmpp.events;

import com.expleague.expert.xmpp.ExpertTask;
import com.expleague.model.Operations;
import com.expleague.xmpp.Item;

import java.util.Date;

/**
 * Experts League
 * Created by solar on 11/02/16.
 */
public class TaskInviteCanceledEvent extends ExpertTaskEvent {
  public TaskInviteCanceledEvent(Item source, ExpertTask task) {
    super(source, task);
  }
}
