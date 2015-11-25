package com.tbts.dao.eb;

import com.tbts.model.Client;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import com.tbts.model.handlers.DAO;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User: solar
 * Date: 11.11.15
 * Time: 15:05
 */
public class EventBusDAO extends DAO {
  private final EventBus<String, CharSequence> bus;

  public EventBusDAO(EventBus<String, CharSequence> bus) {
    super(bus::isMaster);
    this.bus = bus;
  }

  // experts
  @Override
  protected Map<String, Expert> experts() {
    final Map<String, Expert> result = new HashMap<>();
    bus.visitStates((id, state) -> {
      if (state instanceof EBUser) {
        final EBUser user = (EBUser) state;
        if (user.expert() != null)
          result.put(id, user.expert());
      }
      return false;
    });
    return Collections.unmodifiableMap(result);
  }

  @Override
  protected Expert createExpert(String id) {
    EBUser user = bus.state(id);
    if (user == null)
      user = new EBUser(id, bus);
    return user.makeExpert();
  }

  @Override
  protected Expert expert(String id) {
    EventBus.CombinedState<String, CharSequence> state = bus.state(id);
    if (state == null) {
      bus.sync();
      state = bus.state(id);
    }
    return state instanceof EBUser ? ((EBUser) state).expert() : null;
  }

  // clients
  @Override
  protected Map<String, Client> clients() {
    final Map<String, Client> result = new HashMap<>();
    bus.visitStates((id, state) -> {
      if (state instanceof EBUser)
        result.put(id, ((EBUser) state).client());
      return false;
    });
    return Collections.unmodifiableMap(result);
  }

  @Override
  protected Client createClient(String id) {
    final EBUser user = new EBUser(id, bus);
    return user.client();
  }

  @Override
  protected Client client(String id) {
    EventBus.CombinedState<String, CharSequence> state = bus.state(id);
    if (state == null) {
      bus.sync();
      state = bus.state(id);
    }
    return state instanceof EBUser ? ((EBUser) state).client() : null;
  }

  // rooms
  @Override
  protected Map<String, Room> rooms() {
    final Map<String, Room> result = new HashMap<>();
    bus.visitStates((id, state) -> {
      if (state instanceof EBRoom) {
        result.put(id, (Room)state);
      }
      return false;
    });
    return Collections.unmodifiableMap(result);
  }

  @Override
  protected Room createRoom(String id, Client owner) {
    if (!bus.isMaster(owner.id()))
      throw new IllegalStateException("Rooms must be created at master node for its owner");
    return new EBRoom(id, owner, bus);
  }

  @Override
  protected Room room(String id) {
    EventBus.CombinedState<String, CharSequence> state = bus.state(id);
    if (state == null) {
      bus.sync();
      state = bus.state(id);
    }
    return state instanceof EBRoom ? (EBRoom)state: null;
  }
}
