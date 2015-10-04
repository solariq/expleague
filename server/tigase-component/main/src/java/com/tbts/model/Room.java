package com.tbts.model;

import com.spbsu.commons.func.WeakListenerHolder;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 18:50
 */
public interface Room extends WeakListenerHolder<Room.State> {
  String id();

  void query(Query text);
  void answer(Answer answer);

  enum State {
    CLEAN,
    DEPLOYED,
    CHALLENGE,
    LOCKED,
    TIMEOUT,
    COMPLETE,
    FIXED
  }

  State state();
}
