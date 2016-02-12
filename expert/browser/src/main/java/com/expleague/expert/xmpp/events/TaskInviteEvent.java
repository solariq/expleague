package com.expleague.expert.xmpp.events;

import com.expleague.expert.xmpp.ExpertTask;
import com.expleague.model.Operations;
import com.expleague.xmpp.stanza.Stanza;

import java.util.Date;

/**
 * Experts League
 * Created by solar on 11/02/16.
 */
public class TaskInviteEvent extends ExpertTaskEvent {
  private final Date expires;
  public TaskInviteEvent(Operations.Invite source, ExpertTask task, double timeout) {
    super(source, task);
    expires = new Date((long) (timeout * 1000) + System.currentTimeMillis() - 1000);
  }

  public Date expires() {
    return expires;
  }
}
