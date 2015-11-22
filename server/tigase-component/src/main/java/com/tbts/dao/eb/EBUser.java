package com.tbts.dao.eb;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.spbsu.commons.seq.CharSeqTools;
import com.tbts.model.Client;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import com.tbts.model.handlers.Reception;
import com.tbts.model.impl.ClientImpl;
import com.tbts.model.impl.ExpertImpl;
import org.jetbrains.annotations.Nullable;

/**
 * User: solar
 * Date: 11.11.15
 * Time: 15:20
 */
public class EBUser implements EventBus.CombinedState<String, CharSequence> {
  private EBClient client;
  private EBExpert expert;
  private long age;

  public EBUser(String id, EventBus<String, CharSequence> bus){
    client = new EBClient(id);
    bus.register(id, this);
  }

  // needed for marshaling
  public EBUser() {}

  public Expert expert() {
    return expert;
  }

  public Client client() {
    return client;
  }

  public Expert makeExpert() {
    return expert = new EBExpert(client.id(), client.bus);
  }

  @Override
  public long age() {
    return age;
  }

  enum EvtType {
    CLIENT_STATE,
    EXPERT_STATE,
    CLIENT_ACTIVATE,
    EXPERT_JOIN
  }

  @Override
  public EBUser combine(CharSequence x, long time) {
    System.out.println("Combining " + toString() + " with [" + x + "]");
    if (time < age)
      throw new IllegalArgumentException("Can not combine event from the past. My age: " + age + " event time: " + time);
    CharSequence[] split = new CharSequence[2];
    CharSeqTools.split(x, '\t', split);

    final EvtType type = EvtType.valueOf(split[0].toString());
    final String data = split[1].toString();
    switch (type) {
      case CLIENT_STATE:
        client.state(Client.State.valueOf(data));
        break;
      case EXPERT_STATE:
        expert.state(Expert.State.valueOf(data));
        break;
      case CLIENT_ACTIVATE:
        client.activate(Reception.instance().room(data));
        break;
      case EXPERT_JOIN:
        expert.join("null".equals(data) ? null : Reception.instance().room(data));
        break;
    }
    return this;
  }

  @Override
  public void bus(EventBus<String, CharSequence> bus) {
    if (client.bus == null) // initial set up
      client.notifyLastState();

    client.bus = bus;
    if (expert != null) {
      if (expert.bus == null)
        expert.notifyLastState();
      expert.bus = bus;
    }
  }

  @Override
  public void advance(long age) {
    if (age > this.age)
      this.age = age;
  }
}

@JsonIgnoreProperties({"listeners", "lock"})
class EBExpert extends ExpertImpl {
  @JsonIgnore
  public EventBus<String, CharSequence> bus;

  public EBExpert(String id, EventBus<String, CharSequence> bus) {
    super(id);
    this.bus = bus;
  }
  public EBExpert() {
    super();
  }

  @Override
  public void join(@Nullable Room room) {
    bus.log(id(), EBUser.EvtType.EXPERT_JOIN + "\t" + (room != null ? room.id() : null));
    super.join(room);
  }

  @Override
  protected void stateImpl(State state) {
    if (bus == null)
      throw new NullPointerException();
    bus.log(id(), EBUser.EvtType.EXPERT_STATE + "\t" + state);
    super.stateImpl(state);
  }

  public void notifyLastState() {
    invoke(this);
  }
}

@JsonIgnoreProperties({"listeners", "lock"})
class EBClient extends ClientImpl {
  @JsonIgnore
  transient EventBus<String, CharSequence> bus;
  public EBClient(String id) {
    super(id);
  }
  public EBClient() {
    super();
  }

  @Override
  public void activate(Room room) {
    bus.log(id(), EBUser.EvtType.CLIENT_ACTIVATE + "\t" + room.id());
    super.activate(room);
  }

  @Override
  protected void stateImpl(State state) {
    bus.log(id(), EBUser.EvtType.CLIENT_STATE + "\t" + state);
    super.stateImpl(state);
  }

  public void notifyLastState() {
    invoke(this);
  }
}

