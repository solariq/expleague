package com.tbts.model;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.com.tbts.db.Archive;
import com.tbts.com.tbts.db.DAO;

import java.util.HashMap;
import java.util.Map;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:52
 */
public class Reception extends WeakListenerHolderImpl<Room> implements Action<Room> {
  private static Reception keeper;

  public static synchronized Reception instance() {
    if (keeper == null)
      keeper = new Reception();
    return keeper;
  }

  private final Map<String, Room> rooms = new HashMap<>();
  private final Archive archive = new Archive();

  public Archive archive() {
    return archive;
  }

  public Room create(Client client, String roomId) {
    if (roomId.contains("@muc.") || roomId.startsWith("muc."))
      return null;
    final Room result = DAO.instance().createRoom(roomId, client);
    result.addListener(this);
    rooms.put(roomId, result);
    invoke(result);
    return result;
  }

  public Room room(Client client, String to) {
    Room room = rooms.get(to);
    if (room == null)
      room = create(client, to);
    return room;
  }

  @Override
  public void invoke(Room e) {
    super.invoke(e);
  }

  public void clear() {
    rooms.clear();
  }

  public Room room(String jid) {
    return rooms.get(jid);
  }
}
