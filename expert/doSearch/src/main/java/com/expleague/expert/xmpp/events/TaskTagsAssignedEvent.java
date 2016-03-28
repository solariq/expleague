package com.expleague.expert.xmpp.events;

import com.expleague.expert.xmpp.ExpertTask;
import com.expleague.model.Operations;
import com.expleague.model.Tag;

import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 11/02/16.
 */
public class TaskTagsAssignedEvent extends ExpertTaskEvent {
  public TaskTagsAssignedEvent(Operations.Progress source, ExpertTask task) {
    super(source, task);
  }

  public Stream<Tag> tags() {
    return source().assigned();
  }

  @Override
  public Operations.Progress source() {
    return (Operations.Progress)super.source();
  }
}
