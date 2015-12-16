package com.tbts.dao.sql;

import com.spbsu.commons.filters.Filter;
import com.tbts.dao.MySQLOps;
import com.tbts.model.Client;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import com.tbts.model.handlers.ClientManager;
import com.tbts.model.handlers.DAO;
import com.tbts.model.handlers.ExpertManager;
import com.tbts.model.handlers.Reception;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 21.10.15
 * Time: 15:18
 */
public class MySQLDAO extends DAO {
  private static final Logger log = Logger.getLogger(MySQLDAO.class.getName());

  private final Timer timer;
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
    SQLRoom fresh = new SQLRoom(this, id, owner);
    if (isMaster(owner.id())) {
      try {
        final PreparedStatement addRoom = getAddRoom();
        addRoom.setString(1, id);
        addRoom.setString(2, owner.id());
        addRoom.execute();
        roomsMap.put(id, fresh);
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
    return fresh;
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
        if (existing == null) {
          roomsMap.put(id, existing = new SQLRoom(this, id, owner));
          existing.update(state, worker);
        }
        else if (!isMaster(owner.id()))
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
    localUsers.add(id);
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
        if (existing == null) {
          expertsMap.put(id, existing = new SQLExpert(this, id));
          existing.update(state, activeId);
        }
        else if (!isMaster(id))
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
    localUsers.add(id);
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
      final Map<String, Client.State> states = new HashMap<>();
      while (rs.next()) {
        // SELECT clients.id, rooms.id, rooms.owner_state, clients.active_room, clients.state
        final String uid = rs.getString(1);
        final String roomId = rs.getString(2);
        final int state = rs.getInt(3);
        final String active = rs.getString(4);
        if (!uid.equals(currentId) && currentId != null) {
          updateClient(currentId, currentActive, states);
          states.clear();
        }
        currentId = uid;
        currentActive = active;
        if (roomId != null)
          states.put(roomId, Client.State.byIndex(state));
      }
      if (currentId != null) {
        updateClient(currentId, currentActive, states);
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void updateClient(String currentId, String currentActive, Map<String, Client.State> states) {
    SQLClient existing = (SQLClient)clientsMap.get(currentId);
    if (existing == null) {
      clientsMap.put(currentId, existing = new SQLClient(this, currentId));
      existing.update(states, currentActive);
    }
    else if (!isMaster(currentId))
      existing.update(states, currentActive);
  }

  // Cluster stuff
  private final Set<String> localUsers = new HashSet<>();

  public boolean isMaster(String uid) {
    return isUserAvailable(uid) || localUsers.contains(uid);
  }

  private void makeMaster(String uid) {
    localUsers.add(uid);
    final Client client = ClientManager.instance().get(uid);
    Reception.instance().visitClientRooms(client, room -> {
      if (room.state() == Room.State.DEPLOYED)
        ExpertManager.instance().challenge(room);
    });
  }

  private void makeSlave(String uid){
    localUsers.remove(uid);
    final Client client = ClientManager.instance().get(uid);
    Reception.instance().visitClientRooms(client, room -> {
      if (room.state() == Room.State.DEPLOYED)
        ExpertManager.instance().cancelChallenge(room);
    });
  }

  private class NodeOwnershipTask extends TimerTask {
    private final String localHost;

    public NodeOwnershipTask(String localHost) {
      this.localHost = localHost;
    }

    @Override
    public void run() {
      try {
        synchronized (MySQLDAO.this) {
          populateRoomsCache();
          final Set<String> known = new HashSet<>();
          final long now = System.currentTimeMillis();
          {
            final PreparedStatement statement = ops.createStatement("Acquire ownership", "SELECT id, node, heartbeat FROM tbts.Users LEFT JOIN tbts.Connections ON id = user;");
            try (final ResultSet rs = statement.executeQuery()) {
              while (rs.next()) {
                final String uid = rs.getString(1);
                final String node = rs.getString(2);
                Timestamp timestamp = rs.getTimestamp(3);
                final long ts = timestamp != null ? timestamp.getTime() : -1;
                if (timestamp != null)
                  known.add(uid);
                if (node == null || now - ts > TimeUnit.SECONDS.toMillis(60) || isUserAvailable(uid))
                  makeMaster(uid);
                else if (!node.equals(localHost))
                  makeSlave(uid);
              }
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          }
          {
            final PreparedStatement statementUpdate = ops.createStatement("Update ownership", "UPDATE tbts.Connections SET node='" + localHost + "', heartbeat=? WHERE user=?;");
            final PreparedStatement statementInsert = ops.createStatement("Insert ownership", "INSERT INTO tbts.Connections SET user=?, node='" + localHost + "', heartbeat=?;");
            try {
              //noinspection SynchronizationOnLocalVariableOrMethodParameter
              synchronized (statementUpdate) {
                for (final String localUser : localUsers) {
                  if (known.contains(localUser)) {
                    statementUpdate.setTimestamp(1, Timestamp.from(Instant.ofEpochMilli(now)));
                    statementUpdate.setString(2, localUser);
                    statementUpdate.addBatch();
                  }
                  else {
                    statementInsert.setString(1, localUser);
                    statementInsert.setTimestamp(2, Timestamp.from(Instant.ofEpochMilli(now)));
                    statementInsert.addBatch();
                  }
                }
              }
              statementInsert.executeBatch();
              statementUpdate.executeBatch();
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          }
        }
      }
      catch(Exception e) {
        e.printStackTrace();
      }
    }
  }

  // SQL queries definition

  public synchronized void init() {
    System.out.println("DAO init called!");
    populateRoomsCache();
  }

  private final MySQLOps ops;
  public MySQLDAO(String connectionUrl, Filter<String> availability) {
    super(availability);
    ops = new MySQLOps(connectionUrl);
    timer = new Timer("Users ownership daemon", true);
    final String localHost;
    try {
      final InetAddress localHostAddr = InetAddress.getLocalHost();
      localHost = localHostAddr.getHostName();
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
    timer.scheduleAtFixedRate(new NodeOwnershipTask(localHost), 0L, TimeUnit.SECONDS.toMillis(30));
  }

  public PreparedStatement getCheckUser() {
    return ops.createStatement("checkUser", "SELECT * FROM tbts.Users WHERE id=?;");
  }

  public PreparedStatement getAddUser() {
    return ops.createStatement("addUser", "INSERT INTO tbts.Users SET id=?;");
  }

  public PreparedStatement getAddClient() {
    return ops.createStatement("addClient", "INSERT INTO tbts.Clients SET id=?;");
  }

  public PreparedStatement getAddExpert() {
    return ops.createStatement("addExpert", "INSERT INTO tbts.Experts SET id=?;");
  }

  public PreparedStatement getAddRoom() {
    return ops.createStatement("addRoom", "INSERT INTO tbts.Rooms SET id=?, owner=?;");
  }

  public PreparedStatement getListClients() {
    return ops.createStatement("listClients", "SELECT clients.id, rooms.id, rooms.owner_state, clients.active_room, clients.state " +
                                              "FROM tbts.Clients AS clients LEFT OUTER JOIN tbts.Rooms AS rooms " +
                                              "ON clients.id = rooms.owner " +
                                              "GROUP BY clients.id;");
  }

  public PreparedStatement getListExperts() {
    return ops.createStatement("listExperts", "SELECT * FROM tbts.Experts;");
  }

  public PreparedStatement getListRooms() {
    return ops.createStatement("listRooms", "SELECT * FROM tbts.Rooms;");
  }

  public PreparedStatement getUpdateClientState() {
    return ops.createStatement("updateClientState", "UPDATE tbts.Clients SET state=? WHERE id=?;");
  }

  public PreparedStatement getUpdateExpertState() {
    return ops.createStatement("updateExpertState", "UPDATE tbts.Experts SET state=?, active=? WHERE id=?;");
  }

  public PreparedStatement getUpdateRoomState() {
    return ops.createStatement("updateRoomState", "UPDATE tbts.Rooms SET state=? WHERE id=?;");
  }

  public PreparedStatement getUpdateRoomExpert() {
    return ops.createStatement("updateRoomExpert", "UPDATE tbts.Rooms SET worker=? WHERE id=?;");
  }

  public PreparedStatement getUpdateClientActiveRoom() {
    return ops.createStatement("updateClientActiveRoom", "UPDATE tbts.Clients SET active_room=? WHERE id=?;");
  }

  public PreparedStatement getUpdateRoomOwnerState() {
    return ops.createStatement("updateRoomOwnerState", "UPDATE tbts.Rooms SET owner_state=? WHERE id=?;");
  }
}
