package com.tbts.dao.eb;

import com.spbsu.commons.func.Combinator;

/**
 * User: solar
 * Date: 11.11.15
 * Time: 14:18
 */
public interface EventBus<Id, Event> {
  boolean isMaster(Id id);

  <S extends CombinedState<Id, Event>> S state(Id id);
  void register(Id id, CombinedState<Id, Event> state);
  void visitStates(StateVisitor<Id, Event> visitor);

  void log(Id id, Event evt);
  long sync();

  interface CombinedState<Id, Event> extends Combinator <Event> {
    void bus(EventBus<Id, Event> bus);
    void advance(long age);
  }

  interface StateVisitor<Id, Event> {
    boolean accept(Id id, CombinedState<Id, Event> state);
  }
}
