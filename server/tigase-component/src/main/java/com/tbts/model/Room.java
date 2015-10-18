package com.tbts.model;

import com.spbsu.commons.func.WeakListenerHolder;

import java.util.Set;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 18:50
 */
public interface Room extends WeakListenerHolder<Room> {
  String id();

  void text(String text);
  Query query();
  void answer(Answer answer);
  void enterExpert(Expert winner);

  void open();

  boolean quorum(Set<Expert> reserved);

  enum State {
    CLEAN,
    DEPLOYED,
    LOCKED,
    TIMEOUT,
    COMPLETE,
    CANCELED,
    INIT, FIXED
  }

  State state();
}
