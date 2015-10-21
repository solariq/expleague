package com.tbts.com.tbts.db;

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
  public static DAO instance = new DAO();

  public static DAO instance() {
    return instance;
  }

  public com.tbts.model.Client createClient(String id) {
    final Client client = new Client(id);
    clientMap.put(id, client);
    return client;
  }

  public com.tbts.model.Expert createExpert(String id) {
    final Expert expert = new Expert(id);
    expertMap.put(id, expert);
    return expert;
  }

  public com.tbts.model.Room createRoom(String id, com.tbts.model.Client owner) {
    return new Room(id, owner);
  }

  protected Map<String, com.tbts.model.Expert> expertMap = new ConcurrentHashMap<>();
  public Map<String, com.tbts.model.Expert> experts() {
    return Collections.unmodifiableMap(expertMap);
  }

  protected Map<String, com.tbts.model.Client> clientMap = new ConcurrentHashMap<>();
  public Map<String, com.tbts.model.Client> clients() {
    return Collections.unmodifiableMap(clientMap);
  }

  public Map<String, com.tbts.model.Room> roomMap = new ConcurrentHashMap<>();
  public Map<String, com.tbts.model.Room> rooms() {
    return roomMap;
  }

  private static class Client extends ClientImpl {
    public Client(String id) {
      super(id);
    }
  }

  private static class Expert extends ExpertImpl {
    public Expert(String id) {
      super(id);
    }
  }

  private static class Room extends RoomImpl {
    public Room(String id, com.tbts.model.Client owner) {
      super(id, owner);
    }
  }
}
