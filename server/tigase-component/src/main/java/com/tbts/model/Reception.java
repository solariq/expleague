package com.tbts.model;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.com.tbts.db.Archive;
import com.tbts.com.tbts.db.DAO;

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

  private final Archive archive = new Archive();

  public Archive archive() {
    return archive;
  }

  public Room create(Client client, String roomId) {
    if (!roomId.contains("@muc."))
      return null;
    final Room result = DAO.instance().createRoom(roomId, client);
    result.addListener(this);
    invoke(result);
    return result;
  }

  public Room room(Client client, String id) {
    Room room = room(id);
    if (room == null)
      room = create(client, id);
    return room;
  }

  @Override
  public void invoke(Room e) {
    super.invoke(e);
  }

  public Room room(String jid) {
    return DAO.instance().rooms().get(jid);
  }
}
