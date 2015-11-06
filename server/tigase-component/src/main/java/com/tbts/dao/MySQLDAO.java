package com.tbts.dao;

import com.spbsu.commons.filters.Filter;
import com.tbts.model.Client;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import com.tbts.model.handlers.DAO;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 21.10.15
 * Time: 15:18
 */
public class MySQLDAO extends DAO {
  private static final Logger log = Logger.getLogger(MySQLDAO.class.getName());
  private final String connectionUrl;
  // Rooms

  @Override
  protected synchronized Room room(String jid) {
    Room room = roomsMap.get(jid);
    if (room != null)
      return room;
    populateRoomsCache();
    if (roomsMap.get(jid) == null)
      log.warning("Requested room " + jid + " was not found!");
    return roomsMap.get(jid);
  }

  @Override
  protected synchronized Map<String, Room> rooms() {
    populateRoomsCache();
    return roomsMap;
  }

  @Override
  protected synchronized Room createRoom(String id, Client owner) {
    Room existing = room(id);
    if (existing != null)
      return existing;
    try {
      SQLRoom fresh = new SQLRoom(this, id, owner);
      final PreparedStatement addRoom = getAddRoom();
      addRoom.setString(1, id);
      addRoom.setString(2, owner.id());
      addRoom.execute();
      roomsMap.put(id, fresh);
      return fresh;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void populateRoomsCache() {
    populateClientsCache();
    populateExpertsCache();
    try (final ResultSet rs = getListRooms().executeQuery()) {
      while (rs.next()) {
        final String id = rs.getString(1);
        final Client owner = client(rs.getString(2));
        final Room.State state = Room.State.byIndex(rs.getInt(4));
        final String workerId = rs.getString(5);
        final Expert worker = workerId != null ? expert(workerId) : null;
        SQLRoom existing = (SQLRoom) roomsMap.get(id);
        if (existing == null)
          roomsMap.put(id, existing = new SQLRoom(this, id, owner));
        existing.update(state, worker);
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  // Experts

  @Override
  protected synchronized Expert expert(String id) {
    final Expert result = expertsMap.get(id);
    if (result != null)
      return result;
    populateExpertsCache();
    return expertsMap.get(id);
  }

  @Override
  protected synchronized Expert createExpert(String id) {
    final Expert existing = expert(id);
    if (existing != null)
      return existing;
    final Expert fresh = new SQLExpert(this, id);
    try {
      ensureUser(id);
      final PreparedStatement addExpert = getAddExpert();
      addExpert.setString(1, id);
      addExpert.execute();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
    expertsMap.put(id, fresh);
    return fresh;
  }

  @Override
  protected synchronized Map<String, Expert> experts() {
    populateExpertsCache();
    return super.experts();
  }

  private void populateExpertsCache() {
    try (final ResultSet rs = getListExperts().executeQuery()) {
      while (rs.next()) {
        final String id = rs.getString(1);
        final Expert.State state = Expert.State.byIndex(rs.getInt(2));
        final String activeId = rs.getString(3);
        SQLExpert existing = (SQLExpert)expertsMap.get(id);
        if (existing == null)
          expertsMap.put(id, existing = new SQLExpert(this, id));
        existing.update(state, activeId);
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  // Clients

  @Override
  protected synchronized Client client(String id) {
    Client client = clientsMap.get(id);
    if (client != null)
      return client;
    populateClientsCache();
    return clientsMap.get(id);
  }

  protected synchronized Map<String, Client> clients() {
    populateClientsCache();
    return super.clients();
  }

  @Override
  protected synchronized Client createClient(String id) {
    final Client existing = client(id);
    if (existing != null)
      return existing;
    final Client fresh = new SQLClient(this, id);
    try {
      ensureUser(id);
      final PreparedStatement addClient = getAddClient();
      addClient.setString(1, id);
      addClient.execute();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
    clientsMap.put(id, fresh);
    return fresh;
  }

  private void ensureUser(String id) {
    try {
      final PreparedStatement checkUser = getCheckUser();
      checkUser.setString(1, id);
      if (!checkUser.executeQuery().next()) {
        final PreparedStatement addUser = getAddUser();
        addUser.setString(1, id);
        addUser.execute();
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void populateClientsCache() {
    try(final ResultSet rs = getListClients().executeQuery()) {
      String currentId = null;
      String currentActive = null;
      boolean currentOnline = false;
      final Map<String, Client.State> states = new HashMap<>();
      while (rs.next()) {
        // SELECT clients.id, rooms.id, rooms.owner_state, clients.active_room, clients.state
        final String uid = rs.getString(1);
        final String roomId = rs.getString(2);
        final int state = rs.getInt(3);
        final String active = rs.getString(4);
        final boolean online = Client.State.byIndex(rs.getInt(5)) != Client.State.OFFLINE;
        if (!uid.equals(currentId) && currentId != null) {
          SQLClient client = (SQLClient)clientsMap.get(currentId);
          if (client == null)
            clientsMap.put(currentId, client = new SQLClient(this, currentId));
          client.update(states, currentActive);
          states.clear();
        }
        currentId = uid;
        currentActive = active;
        currentOnline = online;
        if (roomId != null)
          states.put(roomId, Client.State.byIndex(state));
      }
      if (currentId != null) {
        SQLClient client = (SQLClient)clientsMap.get(currentId);
        if (client == null)
          clientsMap.put(currentId, client = new SQLClient(this, currentId));
        client.update(states, currentActive);
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  // SQL queries definition

  private Connection conn;
  private final Map<String, PreparedStatement> statements = new ConcurrentHashMap<>();
  private PreparedStatement createStatement(String name, String stmt) {
    PreparedStatement preparedStatement = statements.get(name);
    try {
      if (preparedStatement == null || preparedStatement.isClosed() || preparedStatement.getConnection() == null) {
        if (conn.isClosed())
          conn = DriverManager.getConnection(connectionUrl);
        preparedStatement = conn.prepareStatement(stmt);
      }
      preparedStatement.clearParameters();
      statements.put(name, preparedStatement);
      return preparedStatement;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }

  }

  public void init() {
    System.out.println("DAO init called!");
    populateRoomsCache();
  }

  public MySQLDAO(String connectionUrl, Filter<String> availability) {
    super(availability);
    this.connectionUrl = connectionUrl;
    try {
      conn = DriverManager.getConnection(connectionUrl);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public PreparedStatement getCheckUser() {
    return createStatement("checkUser", "SELECT * FROM tbts.Users WHERE id=?;");
  }

  public PreparedStatement getAddUser() {
    return createStatement("addUser", "INSERT INTO tbts.Users SET id=?;");
  }

  public PreparedStatement getAddClient() {
    return createStatement("addClient", "INSERT INTO tbts.Clients SET id=?;");
  }

  public PreparedStatement getAddExpert() {
    return createStatement("addExpert", "INSERT INTO tbts.Experts SET id=?;");
  }

  public PreparedStatement getAddRoom() {
    return createStatement("addRoom", "INSERT INTO tbts.Rooms SET id=?, owner=?;");
  }

  public PreparedStatement getListClients() {
    return createStatement("listClients", "SELECT clients.id, rooms.id, rooms.owner_state, clients.active_room, clients.state " +
                                              "FROM tbts.Clients AS clients LEFT OUTER JOIN tbts.Rooms AS rooms " +
                                              "ON clients.id = rooms.owner " +
                                              "GROUP BY clients.id;");
  }

  public PreparedStatement getListExperts() {
    return createStatement("listExperts", "SELECT * FROM tbts.Experts;");
  }

  public PreparedStatement getListRooms() {
    return createStatement("listRooms", "SELECT * FROM tbts.Rooms;");
  }

  public PreparedStatement getUpdateClientState() {
    return createStatement("updateClientState", "UPDATE tbts.Clients SET state=? WHERE id=?;");
  }

  public PreparedStatement getUpdateExpertState() {
    return createStatement("updateExpertState", "UPDATE tbts.Experts SET state=?, active=? WHERE id=?;");
  }

  public PreparedStatement getUpdateRoomState() {
    return createStatement("updateRoomState", "UPDATE tbts.Rooms SET state=? WHERE id=?;");
  }

  public PreparedStatement getUpdateRoomExpert() {
    return createStatement("updateRoomExpert", "UPDATE tbts.Rooms SET worker=? WHERE id=?;");
  }

  public PreparedStatement getUpdateClientActiveRoom() {
    return createStatement("updateClientActiveRoom", "UPDATE tbts.Clients SET active_room=? WHERE id=?;");
  }

  public PreparedStatement getUpdateRoomOwnerState() {
    return createStatement("updateRoomOwnerState", "UPDATE tbts.Rooms SET owner_state=? WHERE id=?;");
  }

  static {
    try {
      Class.forName("com.mysql.jdbc.Driver");
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
