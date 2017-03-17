package com.expleague.bots.utils;

import com.expleague.xmpp.Item;
import com.spbsu.commons.filters.Filter;
import com.spbsu.commons.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Artem
 * Date: 10.03.2017
 * Time: 16:17
 */
public class ExpectedMessageBuilder {
  private final List<Pair<Class, Filter>> filters = new ArrayList<>();

  public <T extends Item> ExpectedMessageBuilder has(Class<T> clazz) {
    filters.add(new Pair<>(clazz, null));
    return this;
  }

  public <T extends Item> ExpectedMessageBuilder has(Class<T> clazz, Filter<T> filter) {
    filters.add(new Pair<>(clazz, filter));
    return this;
  }
  public ExpectedMessage build() {
    return new ExpectedMessage(filters);
  }
}