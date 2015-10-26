package com.tbts.model;

import com.spbsu.commons.func.WeakListenerHolder;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 18:46
 */
public interface Client extends WeakListenerHolder<Client> {
  String id();

  Room active();
  void activate(Room room);
  void formulating();
  void query();
  void presence(boolean val);

  enum State {
    OFFLINE(0),
    ONLINE(1),
    FORMULATING(2),
    COMMITED(3),
    TIMEOUT(4),
    FEEDBACK(5),
    CHAT(6);

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
