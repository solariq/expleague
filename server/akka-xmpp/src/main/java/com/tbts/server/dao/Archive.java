package com.tbts.server.dao;

import java.util.Iterator;

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
  public abstract void visitMessages(String id, MessageVisitor visitor);

  /**
   * User: solar
   * Date: 28.10.15
   * Time: 16:58
   */
  public interface MessageVisitor {
    boolean accept(String authorId, CharSequence message, long ts);
  }
}
