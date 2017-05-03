package com.expleague.server.dao.sql;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.expleague.model.Offer;
import com.expleague.model.OrderState;
import com.expleague.model.Tag;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.agents.*;
import com.expleague.server.dao.fake.InMemBoard;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.xmpp.JID;
import com.google.common.base.Joiner;
import com.spbsu.commons.io.StreamTools;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.apache.commons.lang3.text.StrBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.io.IOException;
import java.io.StringReader;
import java.lang.ref.WeakReference;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 05/03/16.
 */
@SuppressWarnings("unused")
public class MySQLBoard extends MySQLOps implements LaborExchange.Board {
  private static final Logger log = Logger.getLogger(MySQLBoard.class.getName());
  public MySQLBoard() {
    super(ExpLeagueServer.config().db());
  }

  private final Map<String, String> replayCheckedRooms = new ConcurrentHashMap<>();
  private final Map<String, WeakReference<MySQLOrder>> orders = new HashMap<>();

  @Override
  public MySQLOrder[] register(Offer offer, int startNo) {
    try {
      final PreparedStatement registerOrder = createStatement("register-order", "INSERT INTO Orders SET id = ?, room = ?, offer = ?, eta = ?, status = " + OrderState.OPEN.code(), false);
      final String id = offer.room().local() + "-" + startNo;
      registerOrder.setString(1, id);
      registerOrder.setString(2, offer.room().local());
      registerOrder.setCharacterStream(3, new StringReader(offer.xmlString()));
      registerOrder.setTimestamp(4, Timestamp.from(offer.expires().toInstant()));
      registerOrder.execute();
      final MySQLOrder order = new MySQLOrder(id, offer);
      order.role(offer.client(), ExpLeagueOrder.Role.OWNER, (long)(offer.started() * 1000));
      return new MySQLOrder[]{order};
    }
    catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public synchronized void removeAllOrders(final String roomId) {
    final PreparedStatement statement = createStatement("delete-room-orders", "DELETE FROM Orders WHERE room = ?");
    try {
      statement.setString(1, roomId);
      statement.execute();
      final Set<String> removed = new HashSet<>();
      orders.forEach((id, orderRef) -> {
        final MySQLOrder order = orderRef.get();
        if (order != null && order.room().local().equals(roomId))
          removed.add(id);
      });
      removed.forEach(orders::remove);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  @Override
  public synchronized ExpLeagueOrder[] active(final String roomId) {
    return replayAwareStream(() -> stream(
      "active-order", "SELECT * FROM Orders WHERE room = ? AND status < " + OrderState.DONE.code(),
      stmt -> stmt.setString(1, roomId)
    ).map(createOrderView())).collect(Collectors.toList()).toArray(new ExpLeagueOrder[0]);
  }

  @Override
  public synchronized Stream<ExpLeagueOrder> history(final String roomId) {
    return replayAwareStream(() -> stream(
      "orders-room", "SELECT * FROM Orders WHERE room = ?",
      statement -> statement.setString(1, roomId)
    ).map(createOrderView()));
  }

  @Override
  public synchronized Stream<ExpLeagueOrder> related(JID jid) {
    return replayAwareStream(() -> stream(
      "related-orders", "SELECT Orders.* FROM Orders WHERE EXISTS(" +
          "SELECT NULL FROM Participants WHERE Orders.id = Participants.`order` AND Participants.partisipant = ?" +
        ")", stmt -> stmt.setString(1, jid.local())
    ).map(createOrderView()));
  }

  @Override
  public Stream<ExpLeagueOrder> open() {
    return orders(new LaborExchange.OrderFilter(false, EnumSet.complementOf(EnumSet.of(OrderState.DONE))));
  }

  @Override
  public synchronized Stream<ExpLeagueOrder> orders(final LaborExchange.OrderFilter filter) {
    final OrderQuery orderQuery = createQuery(filter);
    return replayAwareStream(() -> stream(
      orderQuery.getName(), orderQuery.getSqlQuery(), stmt -> {})
      .map(createOrderView()));
  }

  @Override
  public synchronized void replay(String roomId, ActorContext context) {
    final JID jid = new JID(roomId, "muc." + ExpLeagueServer.config().domain(), null);
    XMPP.whisper(jid, new RoomAgent.Replay(), context);
  }

  @Override
  public Stream<JID> topExperts() {
    return stream("top-experts", "SELECT U.id, count(*) FROM Users AS U " +
        "JOIN Participants AS P ON U.id = P.partisipant " +
        "JOIN Orders AS O ON P.`order` = O.id " +
        "WHERE P.role = " + ExpLeagueOrder.Role.ACTIVE.index() + " GROUP BY U.id", stmt -> {}).map(rs -> {
      try {
        return XMPP.jid(rs.getString(1));
      }
      catch (SQLException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  public Stream<Tag> tags() {
    tags = new TObjectIntHashMap<>();
    stream("all-tags", "SELECT * FROM Tags", q -> {}).forEach(rs -> {
      try {
        tags.put(new Tag(rs.getString(2), rs.getString(3)), rs.getInt(1));
      }
      catch (SQLException e) {
        throw new RuntimeException(e);
      }
    });

    return tags.keySet().stream();
  }

  @Nullable
  @Override
  public LaborExchange.AnswerOfTheWeek answerOfTheWeek() {
    return stream("best-answer", "SELECT room, topic FROM AnswersOfTheWeek WHERE CURRENT_TIME() < DATE_ADD(starts, INTERVAL 1 WEEK) ORDER BY starts DESC", stmt -> {})
        .map(rs -> {
          try {
            return new LaborExchange.AnswerOfTheWeek(rs.getString(1), rs.getString(2));
          }
          catch (SQLException e) {
            throw new RuntimeException(e);
          }
        }).findFirst().orElse(null);
  }

  private TObjectIntHashMap<Tag> tags;
  private int tag(String tagName) {
    final Tag tag = new Tag(tagName);
    if (tags == null || !tags.containsKey(tag))
      tags();
    if (tags.containsKey(tag))
      return tags.get(tag);
    final PreparedStatement statement = createStatement("add-tag", "INSERT Tags SET tag = ?", true);
    try {
      statement.setString(1, tag.name());
      statement.executeUpdate();
      try (final ResultSet gk = statement.getGeneratedKeys()) {
        gk.next();
        final int tagId = gk.getInt(1);
        tags.put(tag, tagId);
        return tagId;
      }
    }
    catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  protected Function<ResultSet, ExpLeagueOrder> createOrderView() {
    return rs -> {
      try {
        final String id = rs.getString(1);
        final WeakReference<MySQLOrder> cached = orders.get(id);
        MySQLOrder result;
        if (cached != null && (result = cached.get()) != null)
          return result;
        result = new MySQLOrder(rs);
        orders.put(id, new WeakReference<>(result));
        return result;
      }
      catch (SQLException | IOException e) {
        throw new RuntimeException(e);
      }
    };
  }

  protected Stream<ExpLeagueOrder> replayAwareStream(final Supplier<Stream<ExpLeagueOrder>> streamSupplier) {
    return streamSupplier.get();
//    final List<ExpLeagueOrder> orders = new ArrayList<>();
//    final Set<String> rooms = new HashSet<>();
//    streamSupplier.get().forEach(order -> {
//      rooms.add(order.offer().room().local());
//      orders.add(order);
//    });
//
//    boolean replayWasExecuted = false;
//    for (String room : rooms) {
//      if (!replayCheckedRooms.containsKey(room)) {
//        synchronized (this) {
//          if (isRoomReplayRequired(room)) {
//            clearRoomBeforeReplay(room);
//            ExpLeagueRoomAgent.replay(this, Archive.instance().dump(room).stream());
//            replayWasExecuted = true;
//          }
//        }
//        replayCheckedRooms.put(room, room);
//      }
//    }
//
//    if (replayWasExecuted) {
//      return streamSupplier.get();
//    }
//    else {
//      return orders.stream();
//    }
  }

  protected boolean isRoomReplayRequired(final String roomId) {
    try {
      final PreparedStatement countOrders = createStatement("count-orders", "SELECT COUNT(*) FROM Orders, OrderStatusHistory WHERE room = ? AND Orders.id = OrderStatusHistory.`order`");
      countOrders.setString(1, roomId);
      final long ordersCount;
      try (final ResultSet countRS = countOrders.executeQuery()){
        countRS.next();
        ordersCount = countRS.getLong(1);
      }
      return ordersCount == 0;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  protected void clearRoomBeforeReplay(final String roomId)  {
    try {
      final PreparedStatement dropOrders = createStatement("drop-orders", "DELETE FROM Orders WHERE room = ?");
      dropOrders.setString(1, roomId);
      dropOrders.execute();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public class MySQLOrder extends InMemBoard.MyOrder {
    private final String id;

    public MySQLOrder(String id, Offer offer) {
      super(offer);
      this.id = id;
    }

    public MySQLOrder(ResultSet resultSet) throws SQLException, IOException {
      super(Offer.create(StreamTools.readReader(resultSet.getCharacterStream(3))));
      id = resultSet.getString(1);
      state = OrderState.valueOf((int)resultSet.getByte(5));
      super.feedback(resultSet.getDouble(6),resultSet.getString(8));
      final Time time = resultSet.getTime(7);
      super.updateActivationTimestampMs(time != null ? time.getTime() : 0);
      final PreparedStatement restoreRoles = createStatement("roles-restore", "SELECT * FROM Participants WHERE `order` = ? ORDER BY id");
      restoreRoles.setString(1, id);
      try (final ResultSet rolesRS = restoreRoles.executeQuery()) {
        while (rolesRS.next()) {
          super.role(XMPP.jid(rolesRS.getString(3)), Role.valueOf(rolesRS.getByte(4)), -1);
        }
      }

      super.statusHistory.clear();
      final PreparedStatement restoreStatusHistory = createStatement("status-history-restore", "SELECT * FROM OrderStatusHistory WHERE `order` = ? ORDER BY id");
      restoreStatusHistory.setString(1, id);
      try (final ResultSet statusHistory = restoreStatusHistory.executeQuery()) {
        while (statusHistory.next()) {
          super.statusHistory.add(new StatusHistoryRecord(
            OrderState.valueOf(statusHistory.getByte(3)), new Date(statusHistory.getTimestamp(4).getTime())
          ));
        }
      }
    }

    public String id() {
      return id;
    }

    @Override
    protected void state(OrderState status, long ts) {
      if (status == this.state)
        return;
      try {
        super.state(status, ts);
        final PreparedStatement statusUpdate = createStatement("status-update", "UPDATE Orders SET status = ? WHERE id = ?");
        statusUpdate.setString(2, id);
        statusUpdate.setInt(1, status.code());
        statusUpdate.execute();

        final PreparedStatement statusHistoryInsert = createStatement("status-history-insert", "INSERT INTO OrderStatusHistory (`order`, status, timestamp) VALUES(?, ?, ?)");
        statusHistoryInsert.setString(1, id);
        statusHistoryInsert.setByte(2, (byte) status.code());
        statusHistoryInsert.setTimestamp(3, new Timestamp(ts));
        statusHistoryInsert.execute();
      }
      catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void feedback(double stars, String payment) {
      try {
        super.feedback(stars, payment);
        final PreparedStatement feedback = createStatement("feedback-role", "UPDATE Orders SET score = ?, payment = ? WHERE id = ?");
        feedback.setString(3, id);
        feedback.setDouble(1, stars);
        feedback.setString(2, payment);
        feedback.execute();
      }
      catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    protected void updateActivationTimestampMs(final long timestamp) {
      try {
        super.updateActivationTimestampMs(timestamp);
        final PreparedStatement activation = createStatement("activation-timestamp", "UPDATE Orders SET activation_timestamp = ? WHERE id = ?");
        activation.setString(2, id);
        activation.setTimestamp(1, new Timestamp(timestamp));
        activation.execute();
      }
      catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }

    boolean tagsAcquired = false;
    @Override
    public Tag[] tags() {
      if (!tagsAcquired) {
        tagsAcquired = true;
        super.tags.addAll(stream("order-topics", "SELECT Tags.tag, Tags.icon FROM Topics JOIN Tags ON Topics.tag = Tags.id WHERE `order` = ?",
            stmt -> stmt.setString(1, id)
        ).map(rs -> {
          try {
            return new Tag(rs.getString(1), rs.getString(2));
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
        }).collect(Collectors.toSet()));
      }
      return super.tags();
    }

    @Override
    protected void role(JID jid, Role role, long ts) {
      super.role(jid, role, ts);
      if (role.permanent()) {
        try {
          final PreparedStatement changeRole = createStatement("change-role", "INSERT INTO Participants SET `order` = ?, partisipant = ?, role = ?, timestamp = ?");
          changeRole.setString(1, id);
          changeRole.setString(2, jid.local());
          changeRole.setByte(3, (byte) role.index());
          changeRole.setTimestamp(4, new Timestamp(ts));
          changeRole.execute();
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      }
    }

    @Override
    protected void tag(String tag) {
      try {
        super.tag(tag);
        final int tagId = MySQLBoard.this.tag(tag);
        final PreparedStatement changeRole = createStatement("append-topic", "INSERT INTO Topics SET `order` = ?, tag = ?");
        changeRole.setString(1, id);
        changeRole.setInt(2, tagId);
        changeRole.execute();
      }
      catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    protected void untag(String tag) {
      try {
        super.tag(tag);
        final int tagId = MySQLBoard.this.tag(tag);
        final PreparedStatement changeRole = createStatement("remove-topic", "DELETE FROM Topics WHERE `order` = ? AND tag = ?");
        changeRole.setString(1, id);
        changeRole.setInt(2, tagId);
        changeRole.execute();
      }
      catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
  }

  protected OrderQuery createQuery(final LaborExchange.OrderFilter filter) {
    final List<String> queryKeys = new ArrayList<>();
    final List<String> conditions = new ArrayList<>();
    final EnumSet<OrderState> statuses = filter.getStatuses();
    if (!statuses.isEmpty()) {
      queryKeys.add(statuses.stream().map(Enum::name).collect(Collectors.joining("-")));
      final String statusesStr = statuses.stream().map(s -> Integer.toString(s.code())).collect(Collectors.joining(","));
      conditions.add("status in (" + statusesStr + ")");
    }
    if (filter.withoutFeedback()) {
      queryKeys.add("without-feedback");
      conditions.add("score = -1");
    }
    final StrBuilder sqlQuery = new StrBuilder("SELECT Orders.* FROM Orders");
    if (!conditions.isEmpty()) {
      sqlQuery.append(" WHERE ").append(Joiner.on(" AND ").join(conditions));
    }
    return new OrderQuery(
      Joiner.on("-").join(queryKeys),
      sqlQuery.toString()
    );
  }

  public static class OrderQuery {
    private final String name;
    private final String sqlQuery;

    public OrderQuery(final String name, final String sqlQuery) {
      this.name = name;
      this.sqlQuery = sqlQuery;
    }

    public String getName() {
      return name;
    }

    public String getSqlQuery() {
      return sqlQuery;
    }
  }
}
