package com.expleague.bots.utils;

import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.spbsu.commons.filters.Filter;
import com.spbsu.commons.util.Pair;

import java.util.List;

/**
 * User: Artem
 * Date: 17.03.2017
 * Time: 14:59
 */
public class ExpectedMessage {
  private final List<Pair<Class, Filter>> filters;
  private final JID from;
  private boolean received = false;

  public ExpectedMessage(JID from, List<Pair<Class, Filter>> filters) {
    this.from = from;
    this.filters = filters;
  }

  public boolean tryReceive(Message message) {
    if (from != null && !from.equals(message.from())) {
      return false;
    }

    //noinspection unchecked
    received = filters.stream().allMatch(pair -> pair.second != null ? message.has(pair.first, pair.second) : message.has(pair.first));
    return received;
  }

  public boolean received() {
    return received;
  }

  public JID from() {
    return from;
  }

  public String toString() {
    final StringBuilder stringBuilder = new StringBuilder();
    filters.forEach(filter -> {
      stringBuilder.append(filter.first.toString());
      stringBuilder.append(", ");
    });
    return stringBuilder.toString();
  }
}
