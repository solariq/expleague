package com.tbts.model.handlers;

import com.tbts.model.Room;

/**
 * User: solar
 * Date: 28.10.15
 * Time: 18:09
 */
public abstract class Archive {
  public static Archive instance;

  public static Archive instance() {
    return instance;
  }

  public abstract void log(String id, String authorId, CharSequence element);
  public void log(Room room, String authorId, CharSequence element) {
    log(room.id(),authorId, element);
  }

  public abstract void visitMessages(String id, MessageVisitor visitor);
  public void visitMessages(Room room, MessageVisitor visitor) {
    visitMessages(room.id(), visitor);
  }

  /**
   * User: solar
   * Date: 28.10.15
   * Time: 16:58
   */
  public interface MessageVisitor {
    boolean accept(String authorId, CharSequence message, long ts);
  }
}
