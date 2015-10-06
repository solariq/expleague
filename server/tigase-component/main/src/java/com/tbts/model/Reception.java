package com.tbts.model;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.model.impl.RoomImpl;
import tigase.xmpp.BareJID;

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

  private final Map<BareJID, Room> rooms = new HashMap<>();

  public Room create(Client client, BareJID roomId) {
    if (!roomId.getDomain().startsWith("muc."))
      return null;
    final Room result = new RoomImpl(roomId.toString(), client);
    result.addListener(this);
    rooms.put(roomId, result);
    invoke(result);
    return result;
  }

  public Room room(Client client, BareJID to) {
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
}
