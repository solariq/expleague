package com.expleague.bots.utils;

import com.expleague.xmpp.AnyHolder;
import com.expleague.xmpp.control.receipts.Request;
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
  private boolean received = false;

  public ExpectedMessage(List<Pair<Class, Filter>> filters) {
    this.filters = filters;
  }

  public boolean tryReceive(AnyHolder anyHolder) {
    //not consider receipts
    final long holderSize = anyHolder.any().stream().filter(a -> !(a instanceof Request)).count();
    if (holderSize != filters.size()) {
      return false;
    }
    //noinspection unchecked
    received = filters.stream().allMatch(pair -> pair.second != null ? anyHolder.has(pair.first, pair.second) : anyHolder.has(pair.first));
    return received;
  }

  public boolean received() {
    return received;
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
