package com.expleague.server.dao.fake;

import com.expleague.model.Offer;
import com.expleague.server.Roster;
import com.expleague.server.agents.ExpLeagueOrder;
import com.expleague.server.agents.LaborExchange;
import com.expleague.xmpp.JID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.expleague.server.agents.ExpLeagueOrder.Role.ACTIVE;
import static com.expleague.server.agents.ExpLeagueOrder.Role.OWNER;

/**
 * Experts League
 * Created by solar on 04/03/16.
 */
@SuppressWarnings("unused")
public class InMemBoard implements LaborExchange.Board {
  private final Map<String, ExpLeagueOrder> active = new ConcurrentHashMap<>();
  private final List<ExpLeagueOrder> history = new CopyOnWriteArrayList<>();

  @Override
  public ExpLeagueOrder active(String roomId) {
    return active.get(roomId);
  }

  @Override
  public ExpLeagueOrder register(Offer offer) {
    final MyOrder order = new MyOrder(offer);
    active.put(order.room().local(), order);
    order.role(offer.client(), OWNER);
    history.add(order);
    return order;
  }

  @Override
  public ExpLeagueOrder[] history(String roomId) {
    final List<ExpLeagueOrder> result = history.stream().filter(
        order -> order.room().local().equals(roomId)
    ).collect(Collectors.toList());
    return result.toArray(new ExpLeagueOrder[result.size()]);
  }

  @Override
  public Stream<ExpLeagueOrder> related(JID jid) {
    return history.stream().filter(o -> o.role(jid) != ExpLeagueOrder.Role.NONE);
  }

  @Override
  public Stream<ExpLeagueOrder> open() {
    return active.values().stream();
  }

  @Override
  public Stream<ExpLeagueOrder> closedWithoutFeedback() {
    return history.stream()
      .filter(o -> o.status() == ExpLeagueOrder.Status.DONE)
      .filter(o -> o.feedback() == -1);
  }

  @Override
  public Stream<JID> topExperts() {
    return history.stream().map(o -> o.of(ACTIVE)).flatMap(s -> s);
  }

  public static class MyOrder extends ExpLeagueOrder {
    private final Map<JID, Role> roles = new HashMap<>();
    protected final Set<String> tags = new HashSet<>();

    protected double score = -1;

    public MyOrder(Offer offer) {
      super(offer);
    }

    @Override
    public void feedback(double stars) {
      score = stars;
    }

    @Override
    public Role role(JID jid) {
      final JID bare = jid.bare();
      return roles.getOrDefault(bare, Role.NONE);
    }

    @Override
    protected void mapTempRoles(Function<Role, Role> map) {
      roles.replaceAll((jid, role) -> map.apply(role));
    }

    @Override
    protected void role(JID bare, Role role) {
      roles.put(bare.bare(), role);
      if (role.permanent()) {
        Roster.instance().invalidateProfile(bare);
      }
    }

    @Override
    protected void tag(String tag) {
      tags.add(tag);
    }

    @Override
    public Stream<JID> of(Role role) {
      return roles.entrySet().stream().filter(
          e -> e.getValue() == role
      ).map(Map.Entry::getKey);
    }

    @Override
    public double feedback() {
      return score;
    }

    @Override
    public String[] tags() {
      return tags.toArray(new String[tags.size()]);
    }

    @Override
    public Stream<JID> participants() {
      return roles.entrySet().stream().filter(
          e -> !EnumSet.of(Role.NONE, Role.DND).contains(e.getValue())
      ).map(Map.Entry::getKey);
    }
  }
}
