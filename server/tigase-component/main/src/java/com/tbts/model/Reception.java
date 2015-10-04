package com.tbts.model;

import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.model.impl.RoomImpl;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:52
 */
public class Reception extends WeakListenerHolderImpl<Room> {
  private static Reception keeper;

  public static synchronized Reception instance() {
    if (keeper == null)
      keeper = new Reception();
    return keeper;
  }

  private volatile int idCounter = 0;
  public Room create(Client client) {
    final Room result = new RoomImpl("" + idCounter++, client);
    invoke(result);
    return result;
  }
}
