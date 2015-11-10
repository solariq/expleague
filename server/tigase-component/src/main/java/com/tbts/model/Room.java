package com.tbts.model;

import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 18:50
 */
public interface Room extends StateWise<Room.State, Room> {
  // Data
  String id();
  Query query();
  Client owner();

  @Nullable
  Expert worker();

  // lifecycle
  void open();
  void enter(Expert winner);
  void answer();
  void cancel();
  void exit();
  void fix();

  // challenge stuff
  void commit();
  boolean relevant(Expert expert);
  boolean quorum(Set<Expert> reserved);
  long invitationTimeout();

  void onMessage(String s, CharSequence element);

  State state();

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
}
