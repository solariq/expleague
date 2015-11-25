package com.tbts.dao.eb;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.spbsu.commons.seq.CharSeqTools;
import com.tbts.model.Client;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import com.tbts.model.impl.RoomImpl;

/**
 * User: solar
 * Date: 11.11.15
 * Time: 15:20
 */

@JsonIgnoreProperties({"listeners", "lock"})
public class EBRoom extends RoomImpl implements EventBus.CombinedState<String, CharSequence> {
  private long age;
  @JsonIgnore
  private transient EventBus<String, CharSequence> bus;

  public EBRoom(String id, Client owner, EventBus<String, CharSequence> bus) {
    super(id, owner);
    this.bus = bus;
    bus.register(id, this);
  }

  public EBRoom() {
    super();
  }

  @Override
  public long age() {
    return age;
  }

  @Override
  public void bus(EventBus<String, CharSequence> bus) {
    if (this.bus == null) // initial set up
      invoke(this);
    this.bus = bus;
  }

  @Override
  public void advance(long age) {
    if (age > this.age)
      this.age = age;
  }

  enum EvtType {
    STATE,
    WORKER,
  }

  @Override
  public void enter(Expert winner) {
    bus.log(id(), EvtType.WORKER + "\t" + winner.id());
    super.enter(winner);
  }

  @Override
  protected void stateImpl(State newState) {
    bus.log(id(), EvtType.STATE + "\t" + newState);
    super.stateImpl(newState);
  }

  @Override
  public EBRoom combine(CharSequence x, long time) {
    System.out.println("Combining " + toString() + " with [" + x + "]");

    CharSequence[] split = new CharSequence[2];
    CharSeqTools.split(x, '\t', split);
    String data = split[1].toString();
    final EvtType type = EvtType.valueOf(split[0].toString());
    switch (type) {
      case STATE:
        state(Room.State.valueOf(data));
        break;
      case WORKER:
        worker = data;
        break;
    }
    age = time;
    return this;
  }
}
