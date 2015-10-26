package com.tbts.com.tbts.db.impl;

import com.tbts.model.Client;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import com.tbts.model.handlers.DAO;

import java.sql.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User: solar
 * Date: 21.10.15
 * Time: 15:18
 */
public class MySQLDAO extends DAO {
  private static final String CONNECTION_URL = "jdbc:mysql://localhost:3307/tbts?user=tigase&password=tg30239&useUnicode=true&characterEncoding=UTF-8&autoCreateUser=true";

  // Rooms

  @Override
  protected Room room(String jid) {
    Room room = roomsMap.get(jid);
    if (room != null)
      return room;
    populateRoomsCache();
    return roomsMap.get(jid);
  }

  @Override
  protected Map<String, Room> rooms() {
    populateRoomsCache();
    return roomsMap;
  }

  @Override
  protected Room createRoom(String id, Client owner) {
    Room existing = room(id);
    if (existing != null)
      return existing;
    try {
      SQLRoom fresh = new SQLRoom(this, id, owner, null, Room.State.CLEAN);
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
    try {
      final ResultSet rs = listRooms.executeQuery();
      while (rs.next()) {
        final String id = rs.getString("id");
        final Client owner = client(rs.getString("owner"));
        final String activeExpertId = rs.getString("active_expert");
        final Expert active_expert = activeExpertId != null ? expert(activeExpertId) : null;
        final Room.State state = Room.State.byIndex(rs.getInt("state"));
        final SQLRoom existing = (SQLRoom) roomsMap.get(id);
        if (existing != null)
          existing.update(state, active_expert);
        else
          roomsMap.put(id, new SQLRoom(this, id, owner, active_expert, state));
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
    final Expert fresh = new SQLExpert(this, id, Expert.State.AWAY, null);
    try {
      ensureUser(id);
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
    try {
      final ResultSet rs = listExperts.executeQuery();
      while (rs.next()) {
        final String id = rs.getString(1);
        final Expert.State state = Expert.State.byIndex(rs.getInt(2));
        final String activeId = rs.getString(3);
        final SQLExpert existing = (SQLExpert)expertsMap.get(id);
        if (existing != null)
          existing.update(state, activeId);
        else
          expertsMap.put(id, new SQLExpert(this, id, state, activeId));
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
    final Client fresh = new SQLClient(this, id, false, Collections.emptyMap(), null);
    try {
      ensureUser(id);
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
      checkUser.setString(1, id);
      if (!checkUser.executeQuery().next()) {
        addUser.setString(1, id);
        addUser.execute();
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void populateClientsCache() {
    try {
      final ResultSet rs = listClients.executeQuery();

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
          final SQLClient client = (SQLClient)clientsMap.get(currentId);
          if (client == null)
            clientsMap.put(currentId, new SQLClient(this, currentId, currentOnline, states, currentActive));
          else
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
        final SQLClient client = (SQLClient)clientsMap.get(currentId);
        if (client == null)
          clientsMap.put(currentId, new SQLClient(this, currentId, currentOnline, states, currentActive));
        else
          client.update(states, currentActive);
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  // SQL queries definition

  private final PreparedStatement checkUser;
  private final PreparedStatement addUser;
  private final PreparedStatement addClient;
  private final PreparedStatement addExpert;
  private final PreparedStatement addRoom;
  private final PreparedStatement listClients;
  private final PreparedStatement listExperts;
  private final PreparedStatement listRooms;
  final PreparedStatement updateClientState;
  final PreparedStatement updateExpertState;
  final PreparedStatement updateRoomState;
  final PreparedStatement updateClientActiveRoom;
  final PreparedStatement updateRoomOwnerState;
  final PreparedStatement updateActiveExpert;

  public MySQLDAO() {
    try {
      final Connection conn = DriverManager.getConnection(CONNECTION_URL);
      checkUser = conn.prepareStatement("SELECT * FROM tbts.Users WHERE id=?;");
      addUser = conn.prepareStatement("INSERT INTO tbts.Users SET id=?;");
      addClient = conn.prepareStatement("INSERT INTO tbts.Clients SET id=?;");
      addExpert = conn.prepareStatement("INSERT INTO tbts.Experts SET id=?;");
      addRoom = conn.prepareStatement("INSERT INTO tbts.Rooms SET id=?, owner=?;");
      listClients = conn.prepareStatement("SELECT clients.id, rooms.id, rooms.owner_state, clients.active_room, clients.state " +
                                              "FROM tbts.Clients AS clients LEFT OUTER JOIN tbts.Rooms AS rooms " +
                                                "ON clients.id = rooms.owner " +
                                              "GROUP BY Clients.id;");
      listExperts = conn.prepareStatement("SELECT e.id, e.state, r.id FROM tbts.Experts AS e LEFT JOIN tbts.Rooms AS r ON e.id = r.active_expert;");
      listRooms = conn.prepareStatement("SELECT * FROM tbts.Rooms;");
      updateClientState = conn.prepareStatement("UPDATE tbts.Experts SET state=? WHERE id=?;");
      updateExpertState = conn.prepareStatement("UPDATE tbts.Clients SET state=? WHERE id=?;");
      updateRoomState = conn.prepareStatement("UPDATE tbts.Rooms SET state=? WHERE id=?;");
      updateActiveExpert = conn.prepareStatement("UPDATE tbts.Rooms SET active_expert=? WHERE id=?;");
      updateClientActiveRoom = conn.prepareStatement("UPDATE tbts.Clients SET active_room=? WHERE id=?;");
      updateRoomOwnerState = conn.prepareStatement("UPDATE tbts.Rooms SET owner_state=? WHERE id=?;");
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }


  static {
    try {
      Class.forName("com.mysql.jdbc.Driver");
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
