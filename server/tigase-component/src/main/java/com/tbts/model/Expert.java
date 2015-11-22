package com.tbts.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 18:49
 */
public interface Expert extends StateWise<Expert.State, Expert> {
  String id();

  void online(boolean val);

  Room active();
  boolean reserve(Room room);
  void steady();
  void invite();
  void ask();
  void free();

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
