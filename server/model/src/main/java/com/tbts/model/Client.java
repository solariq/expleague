package com.tbts.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jetbrains.annotations.Nullable;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 18:46
 */
public interface Client extends StateWise<Client.State, Client> {
  String id();

  @Nullable
  Room active();
  State state();

  // lifecycle
  void online(boolean val);

  void activate(Room room);
  void query();
  void feedback(Room room);

  enum State {
    OFFLINE(0),
    ONLINE(1),
    FORMULATING(2),
    COMMITED(3),
    TIMEOUT(4),
    FEEDBACK(5);

    private final int index;

    State(int index) {
      this.index = index;
    }

    @JsonValue
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

    @JsonCreator
    public static State byIndex(int state) {
      return states[state];
    }
  }
}
