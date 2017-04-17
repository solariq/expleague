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
public class ReceivingMessageBuilder {
  private final List<Pair<Class, Filter>> filters = new ArrayList<>();
  private JID from = null;
  private boolean expected = true;

  public <T extends Item> ReceivingMessageBuilder has(Class<T> clazz) {
    filters.add(new Pair<>(clazz, null));
    return this;
  }

  public <T extends Item> ReceivingMessageBuilder has(Class<T> clazz, Filter<T> filter) {
    filters.add(new Pair<>(clazz, filter));
    return this;
  }

  public ReceivingMessageBuilder from(BareJID from) {
    this.from = JID.parse(from.toString());
    return this;
  }

  public ReceivingMessageBuilder from(JID from) {
    this.from = from;
    return this;
  }

  public ReceivingMessageBuilder expected(boolean expected) {
    this.expected = expected;
    return this;
  }

  public ReceivingMessage build() {
    return new ReceivingMessage(from, filters, expected);
  }
}