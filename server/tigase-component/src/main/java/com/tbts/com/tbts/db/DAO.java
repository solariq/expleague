package com.tbts.com.tbts.db;

import com.tbts.model.impl.ClientImpl;
import com.tbts.model.impl.ExpertImpl;
import com.tbts.model.impl.RoomImpl;

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
    return new Client(id);
  }

  public com.tbts.model.Expert createExpert(String id) {
    return new Expert(id);
  }

  public com.tbts.model.Room createRoom(String id, com.tbts.model.Client owner) {
    return new Room(id, owner);
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
