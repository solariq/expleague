package com.tbts.model;

import com.spbsu.commons.func.WeakListenerHolder;

import java.util.List;
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
  void enter(Expert winner);

  void open();

  boolean quorum(Set<Expert> reserved);

  void invite(Expert next);

  boolean relevant(Expert expert);

  long invitationTimeout();

  void exit(Expert expert);

  List<Answer> answers();

  void onMessage(String s, CharSequence element);

  enum State {
    CLEAN(0),
    DEPLOYED(1),
    LOCKED(2),
    TIMEOUT(3),
    COMPLETE(4),
    CANCELED(5),
    INIT(6),
    FIXED(7);


    private final int index;

    State(int index) {
      this.index = index;
    }

    public int index() {
      return index;
    }
    static State[] states;
    static {
      final State[] values = State.values();
      states = new State[values.length];
      for (final State value : values) {
        states[value.index()] = value;
      }
    }

    public static State byIndex(int state) {
      return states[state];
    }
  }

  State state();
}
