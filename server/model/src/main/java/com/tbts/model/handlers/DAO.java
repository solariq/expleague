package com.tbts.model.handlers;

import com.spbsu.commons.filters.Filter;
import com.tbts.model.Client;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import com.tbts.model.impl.ClientImpl;
import com.tbts.model.impl.ExpertImpl;
import com.tbts.model.impl.RoomImpl;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: solar
 * Date: 21.10.15
 * Time: 14:55
 */
public class DAO {
  public static DAO instance;
  public Filter<String> checkAvailability;

  protected DAO(final Filter<String> availabilityChecker) {
    checkAvailability = availabilityChecker;
  }

  protected static DAO instance() {
    return instance;
  }

  protected final Map<String, Expert> expertsMap = new ConcurrentHashMap<>();

  protected Map<String, Expert> experts() {
    return Collections.unmodifiableMap(expertsMap);
  }

  protected Expert createExpert(String id) {
    final MyExpert expert = new MyExpert(id);
    expertsMap.put(id, expert);
    return expert;
  }

  protected Expert expert(String id) {
    return expertsMap.get(id);
  }

  protected final Map<String, Client> clientsMap = new ConcurrentHashMap<>();

  protected Map<String, Client> clients() {
    return Collections.unmodifiableMap(clientsMap);
  }

  protected Client createClient(String id) {
    final MyClient client = new MyClient(id);
    clientsMap.put(id, client);
    return client;
  }

  protected Client client(String jid) {
    return clientsMap.get(jid);
  }


  protected final Map<String, Room> roomsMap = new ConcurrentHashMap<>();

  protected Map<String, Room> rooms() {
    return roomsMap;
  }

  protected Room createRoom(String id, Client owner) {
    final Room room = new MyRoom(id, owner);
    roomsMap.put(id, room);
    return room;
  }

  protected Room room(String jid) {
    return roomsMap.get(jid);
  }

  public void init() {}

  public boolean isUserAvailable(String id) {
    return checkAvailability.accept(id);
  }

  private static class MyClient extends ClientImpl {
    public MyClient(String id) {
      super(id);
    }
  }

  private static class MyExpert extends ExpertImpl {
    public MyExpert(String id) {
      super(id);
    }
  }

  private static class MyRoom extends RoomImpl {
    public MyRoom(String id, com.tbts.model.Client owner) {
      super(id, owner);
    }
  }
}
