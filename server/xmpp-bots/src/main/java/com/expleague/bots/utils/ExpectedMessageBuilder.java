package com.expleague.bots.utils;

import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.spbsu.commons.filters.Filter;
import com.spbsu.commons.util.Pair;
import tigase.jaxmpp.core.client.BareJID;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Artem
 * Date: 10.03.2017
 * Time: 16:17
 */
public class ExpectedMessageBuilder {
  private final List<Pair<Class, Filter>> filters = new ArrayList<>();
  private JID from = null;

  public <T extends Item> ExpectedMessageBuilder has(Class<T> clazz) {
    filters.add(new Pair<>(clazz, null));
    return this;
  }

  public <T extends Item> ExpectedMessageBuilder has(Class<T> clazz, Filter<T> filter) {
    filters.add(new Pair<>(clazz, filter));
    return this;
  }

  public ExpectedMessageBuilder from(BareJID from) {
    this.from = JID.parse(from.toString());
    return this;
  }

  public ExpectedMessageBuilder from(JID from) {
    this.from = from;
    return this;
  }

  public ExpectedMessage build() {
    if (from == null) {
      throw new IllegalStateException("from is not specified");
    }
    return new ExpectedMessage(from, filters);
  }
}