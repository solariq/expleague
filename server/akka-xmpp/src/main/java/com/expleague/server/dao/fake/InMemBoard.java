package com.expleague.server.dao.fake;

import com.expleague.model.Offer;
import com.expleague.model.Tag;
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
  public Stream<ExpLeagueOrder> history(String roomId) {
    return history.stream().filter(order -> order.room().local().equals(roomId));
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
  public Stream<ExpLeagueOrder> orders(LaborExchange.OrderFilter filter) {
    return history.stream()
      .filter(o -> filter.getStatuses().isEmpty() || filter.getStatuses().contains(o.status()))
      .filter(o -> !filter.withoutFeedback() || o.feedback() == -1);
  }

  @Override
  public Stream<JID> topExperts() {
    return history.stream().map(o -> o.of(ACTIVE)).flatMap(s -> s);
  }

  @Override
  public Stream<Tag> tags() {
    return history.stream().flatMap(o -> Stream.of(o.tags())).collect(Collectors.toSet()).stream();
  }

  @Override
  public LaborExchange.AnswerOfTheWeek answerOfTheWeek() {
    return history.isEmpty() ? null : new LaborExchange.AnswerOfTheWeek(history.get(0).room().local(), history.get(0).offer().topic());
  }

  public static class MyOrder extends ExpLeagueOrder {
    private final Map<JID, Role> roles = new HashMap<>();
    protected final Set<Tag> tags = new HashSet<>();
    protected final List<StatusHistoryRecord> statusHistory = new ArrayList<>();

    protected double score = -1;
    protected long activationTimestampMs = 0;

    public MyOrder(Offer offer) {
      super(offer);
    }

    @Override
    protected void status(final Status status) {
      super.status(status);
      statusHistory.add(new StatusHistoryRecord(status, new Date(currentTimestampMillis())));
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
      tags.add(new Tag(tag));
    }

    @Override
    protected void untag(String tag) {
      tags.remove(new Tag(tag));
    }

    @Override
    protected void updateActivationTimestampMs(final long timestamp) {
      activationTimestampMs = timestamp;
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
    public Tag[] tags() {
      return tags.toArray(new Tag[tags.size()]);
    }

    @Override
    public Stream<StatusHistoryRecord> statusHistoryRecords() {
      return statusHistory.stream();
    }

    @Override
    public long getActivationTimestampMs() {
      return activationTimestampMs;
    }

    @Override
    public Stream<JID> participants() {
      return roles.entrySet().stream().filter(
          e -> !EnumSet.of(Role.NONE, Role.DND).contains(e.getValue())
      ).map(Map.Entry::getKey);
    }
  }
}
