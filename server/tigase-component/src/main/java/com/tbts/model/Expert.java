package com.tbts.model;

import com.spbsu.commons.func.WeakListenerHolder;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 18:49
 */
public interface Expert extends WeakListenerHolder<Expert> {
  String id();

  void online(boolean val);

  boolean reserve(Room room);

  void invite();

  void ask(Room room);

  Room active();
  void free();
  void steady();
  void answer(Answer answer);
  State state();


  enum State {
    AWAY(0),
    READY(1),
    CHECK(2),
    STEADY(3),
    INVITE(4),
    DENIED(5),
    CANCELED(6),
    GO(7);

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
}
