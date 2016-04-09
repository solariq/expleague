package com.expleague.server.dao.sql;

import com.expleague.model.Offer;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.agents.ExpLeagueOrder;
import com.expleague.server.agents.ExpLeagueRoomAgent;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.agents.XMPP;
import com.expleague.server.dao.fake.InMemBoard;
import com.expleague.xmpp.JID;
import com.google.common.base.Joiner;
import com.spbsu.commons.io.StreamTools;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.apache.commons.lang3.text.StrBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 05/03/16.
 */
@SuppressWarnings("unused")
public class MySQLBoard extends MySQLOps implements LaborExchange.Board {
  public MySQLBoard() {
    super(ExpLeagueServer.config().db());
  }

  private final Map<String, String> replayCheckedRooms = new ConcurrentHashMap<>();

  @Override
  public MySQLOrder register(Offer offer) {
    try {
      final PreparedStatement registerOrder = createStatement("register-order", "INSERT INTO Orders SET room = ?, offer = ?, eta = ?, status = " + ExpLeagueOrder.Status.OPEN.index(), true);
      registerOrder.setString(1, offer.room().local());
      registerOrder.setCharacterStream(2, new StringReader(offer.xmlString()));
      registerOrder.setTimestamp(3, Timestamp.from(offer.expires().toInstant()));
      registerOrder.execute();
      final MySQLOrder result;
      try (final ResultSet generatedKeys = registerOrder.getGeneratedKeys()) {
        generatedKeys.next();
        final int id = generatedKeys.getInt(1);
        result = new MySQLOrder(id, offer);
      }
      result.role(offer.client(), ExpLeagueOrder.Role.OWNER);
      result.status(ExpLeagueOrder.Status.OPEN);
      return result;
    }
    catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  @Override
  public ExpLeagueOrder active(final String roomId) {
    return replayAwareStream(() -> stream(
      "active-order", "SELECT * FROM Orders WHERE room = ? AND status < " + ExpLeagueOrder.Status.DONE.index(),
      stmt -> stmt.setString(1, roomId)
    ).map(createOrderView())).findFirst().orElse(null);
  }

  @Override
  public Stream<ExpLeagueOrder> history(final String roomId) {
    return replayAwareStream(() -> stream(
      "orders-room", "SELECT * FROM Orders WHERE room = ?",
      statement -> statement.setString(1, roomId)
    ).map(createOrderView()));
  }

  @Override
  public Stream<ExpLeagueOrder> related(JID jid) {
    return replayAwareStream(() -> stream(
      "related-orders", "SELECT Orders.* " +
        "FROM Orders INNER JOIN Participants ON Orders.id = Participants.`order` " +
        "WHERE Participants.partisipant = ?", stmt -> stmt.setString(1, jid.local())
    ).map(createOrderView()));
  }

  @Override
  public Stream<ExpLeagueOrder> open() {
    return orders(new LaborExchange.OrderFilter(false, EnumSet.complementOf(EnumSet.of(ExpLeagueOrder.Status.DONE))));
  }

  @Override
  public Stream<ExpLeagueOrder> orders(final LaborExchange.OrderFilter filter) {
    final OrderQuery orderQuery = createQuery(filter);
    return replayAwareStream(() -> stream(
      orderQuery.getName(), orderQuery.getSqlQuery(), stmt -> {})
      .map(createOrderView()));
  }

  @Override
  public Stream<JID> topExperts() {
    return stream("top-experts", "SELECT U.id, count(*) FROM Users AS U " +
        "JOIN Participants AS P ON U.id = P.partisipant " +
        "JOIN Orders AS O ON P.`order` = O.id " +
        "WHERE P.role = " + ExpLeagueOrder.Role.ACTIVE.index() + " GROUP BY U.id", stmt -> {}).map(rs -> {
      try {
        return XMPP.jid(rs.getString(1));
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  public Stream<String> tags() {
    tags = new TObjectIntHashMap<>();
    stream("all-tags", "SELECT * FROM Tags", q -> {}).forEach(rs -> {
      try {
        tags.put(rs.getString(2), rs.getInt(1));
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    });

    return tags.keySet().stream();
  }

  private TObjectIntHashMap<String> tags;
  private int tag(String tag) {
    if (tags == null || !tags.containsKey(tag))
      tags();
    if (tags.containsKey(tag))
      return tags.get(tag);
    final PreparedStatement statement = createStatement("add-tag", "INSERT Tags SET tag = ?", true);
    try {
      statement.setString(1, tag);
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
        return new MySQLOrder(rs);
      }
      catch (SQLException | IOException e) {
        throw new RuntimeException(e);
      }
    };
  }

  protected Stream<ExpLeagueOrder> replayAwareStream(final Supplier<Stream<ExpLeagueOrder>> streamSupplier) {
    final List<ExpLeagueOrder> orders = new ArrayList<>();
    final Set<String> rooms = new HashSet<>();
    streamSupplier.get().forEach(order -> {
      rooms.add(order.offer().room().local());
      orders.add(order);
    });

    boolean replayWasExecuted = false;
    for (String room : rooms) {
      if (!replayCheckedRooms.containsKey(room)) {
        synchronized (this) {
          if (isRoomReplayRequired(room)) {
            clearRoomBeforeReplay(room);
            ExpLeagueRoomAgent.replay(this, room);
            replayWasExecuted = true;
          }
        }
        replayCheckedRooms.put(room, room);
      }
    }

    if (replayWasExecuted) {
      return streamSupplier.get();
    }
    else {
      return orders.stream();
    }
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
    private final int id;

    public MySQLOrder(int id, Offer offer) {
      super(offer);
      this.id = id;
    }

    public MySQLOrder(ResultSet resultSet) throws SQLException, IOException {
      super(Offer.create(StreamTools.readReader(resultSet.getCharacterStream(3))));
      id = resultSet.getInt(1);
      super.status(Status.valueOf((int)resultSet.getByte(5)));
      super.feedback(resultSet.getDouble(6));
      final PreparedStatement restoreRoles = createStatement("roles-restore", "SELECT * FROM Participants WHERE `order` = ? ORDER BY id");
      restoreRoles.setInt(1, id);
      try (final ResultSet rolesRS = restoreRoles.executeQuery()) {
        while (rolesRS.next()) {
          super.role(XMPP.jid(rolesRS.getString(3)), Role.valueOf(rolesRS.getByte(4)));
        }
      }
      final PreparedStatement restoreStatusHistory = createStatement("status-history-restore", "SELECT * FROM OrderStatusHistory WHERE `order` = ? ORDER BY id");
      restoreStatusHistory.setInt(1, id);
      try (final ResultSet statusHistory = restoreStatusHistory.executeQuery()) {
        while (statusHistory.next()) {
          super.statusHistory.add(new StatusHistoryRecord(
            Status.valueOf(statusHistory.getByte(3)), new Date(statusHistory.getTimestamp(4).getTime())
          ));
        }
      }
    }

    @Override
    protected void status(Status status) {
      try {
        super.status(status);
        final PreparedStatement statusUpdate = createStatement("status-update", "UPDATE Orders SET status = ? WHERE id = ?");
        statusUpdate.setInt(2, id);
        statusUpdate.setInt(1, status.index());
        statusUpdate.execute();

        final PreparedStatement statusHistoryInsert = createStatement("status-history-insert", "INSERT INTO OrderStatusHistory (`order`, status, timestamp) VALUES(?, ?, ?)");
        statusHistoryInsert.setInt(1, id);
        statusHistoryInsert.setByte(2, (byte) status.index());
        statusHistoryInsert.setTimestamp(3, new Timestamp(currentTimestampMillis()));
        statusHistoryInsert.execute();
      }
      catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void feedback(double stars) {
      try {
        super.feedback(stars);
        final PreparedStatement feedback = createStatement("feedback-role", "UPDATE Orders SET score = ? WHERE id = ?");
        feedback.setInt(2, id);
        feedback.setDouble(1, stars);
        feedback.execute();
      }
      catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }

    boolean tagsAcquired = false;
    @Override
    public String[] tags() {
      if (!tagsAcquired) {
        tagsAcquired = true;
        super.tags.addAll(stream("order-topics", "SELECT Tags.tag FROM Topics JOIN Tags ON Topics.tag = Tags.id WHERE `order` = ?",
            stmt -> stmt.setInt(1, id)
        ).map(rs -> {
          try {
            return rs.getString(1);
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
        }).collect(Collectors.toSet()));
      }
      return super.tags();
    }

    @Override
    protected void role(JID jid, Role role) {
      super.role(jid, role);
      if (role.permanent()) {
        try {
          final PreparedStatement changeRole = createStatement("change-role", "INSERT INTO Participants SET `order` = ?, partisipant = ?, role = ?, timestamp = ?");
          changeRole.setInt(1, id);
          changeRole.setString(2, jid.local());
          changeRole.setByte(3, (byte) role.index());
          changeRole.setTimestamp(4, new Timestamp(currentTimestampMillis()));
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
        changeRole.setInt(1, id);
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
        changeRole.setInt(1, id);
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
    final EnumSet<ExpLeagueOrder.Status> statuses = filter.getStatuses();
    if (!statuses.isEmpty()) {
      queryKeys.add(statuses.stream().map(Enum::name).collect(Collectors.joining("-")));
      final String statusesStr = statuses.stream().map(s -> Integer.toString(s.index())).collect(Collectors.joining(","));
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
