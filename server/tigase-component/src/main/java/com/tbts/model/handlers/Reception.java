package com.tbts.model.handlers;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.model.Client;
import com.tbts.model.Room;

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

  public Room create(Client client, String roomId) {
    if (!roomId.contains("@muc."))
      return null;
    Room result = DAO.instance().room(roomId);
    if (result == null) {
      result = DAO.instance().createRoom(roomId, client);
      invoke(result);
    }
    return result;
  }

  public Room room(Client client, String id) {
    if (!id.contains("@muc."))
      return null;
    Room room = room(id);
    if (room == null)
      room = create(client, id);
    return room;
  }

  @Override
  public void invoke(Room e) {
    if (e.state() == Room.State.DEPLOYED)
      ExpertManager.instance().challenge(e);
    super.invoke(e);
  }

  public Room room(String jid) {
    if (!jid.contains("@muc."))
      return null;
    return DAO.instance().room(jid);
  }
}
