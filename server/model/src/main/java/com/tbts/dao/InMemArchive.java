package com.tbts.dao;

import com.tbts.model.handlers.Archive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: solar
 * Date: 16.11.15
 * Time: 22:36
 */
public class InMemArchive extends Archive {
  final Map<String, List<Msg>> map = new HashMap<>();

  @Override
  public void log(String id, String authorId, CharSequence element) {
    List<Msg> msgs = map.get(id);
    if (msgs == null)
      map.put(id, msgs = new ArrayList<>());
    msgs.add(new Msg(authorId, element, System.currentTimeMillis()));
  }

  @Override
  public void visitMessages(String id, MessageVisitor visitor) {
    final List<Msg> msgs = map.get(id);
    if (msgs != null) {
      for (final Msg msg : msgs) {
        visitor.accept(msg.author, msg.message, msg.ts);
      }
    }
  }

  public void clear() {
    map.clear();
  }

  class Msg {
    String author;
    long ts;
    CharSequence message;

    public Msg(String author, CharSequence message, long ts) {
      this.author = author;
      this.message = message;
      this.ts = ts;
    }
  }
}
