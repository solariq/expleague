package com.expleague.server.dao.sql;

import com.expleague.model.Offer;
import com.expleague.model.OrderState;
import com.expleague.server.ExpLeagueServerTestCase;
import com.expleague.server.agents.ExpLeagueOrder;
import com.expleague.server.agents.LaborExchange;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.register.RegisterQuery;
import com.expleague.xmpp.stanza.Message;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author vpdelta
 */
public class MySQLOrderTest {
  private MySQLRoster roster;
  private MySQLBoard board;
  private MySQLTestOps mySQLTestOps;

  @Before
  public void setUp() throws Exception {
    ExpLeagueServerTestCase.setUpTestConfig();
    mySQLTestOps = new MySQLTestOps();
    mySQLTestOps.setUp();

    roster = new MySQLRoster();
    board = new MySQLBoard();
  }

  @Test
  public void testRegisterOrderAddTag() throws Exception {
    final JID client = registerUser("x@b.c");
    final JID room = JID.parse("a@b.c");
    final MySQLBoard.MySQLOrder[] order = board.register(Offer.create(
      room,
      client,
      new Message(client, room, new Message.Subject("offer"))
    ));
    order[0].tag("tag");
    assertEquals("tag", order[0].tags()[0].name());
  }

  @Test
  public void testRegisterOrderReplayAddTag() throws Exception {
    final JID client = registerUser("x@b.c");
    final JID room = JID.parse("a@b.c");
    final MySQLBoard.MySQLOrder[] order = board.register(Offer.create(
      room,
      client,
      new Message(client, room, new Message.Subject("offer"))
    ));
    board.open().collect(Collectors.toList());
    order[0].tag("tag");
    assertEquals("tag", order[0].tags()[0].name());
  }

  @Test
  public void testRegisterOrderSetStatusReplayAndCheck() throws Exception {
    final JID client = registerUser("x@b.c");
    final JID room = JID.parse("a@b.c");
    final MySQLBoard.MySQLOrder order[] = board.register(Offer.create(
      room,
      client,
      new Message(client, room, new Message.Subject("offer"))
    ));
    order[0].state(OrderState.DONE, System.currentTimeMillis());
    final ExpLeagueOrder expLeagueOrder = board.orders(new LaborExchange.OrderFilter(false, EnumSet.allOf(OrderState.class))).findFirst().orElse(null);
    assertEquals(OrderState.DONE, expLeagueOrder.state());
    final List<ExpLeagueOrder.StatusHistoryRecord> statusHistoryRecords = expLeagueOrder.statusHistoryRecords().collect(Collectors.toList());
    assertEquals(2, statusHistoryRecords.size());
    assertEquals(OrderState.OPEN, statusHistoryRecords.get(0).getStatus());
    assertEquals(OrderState.DONE, statusHistoryRecords.get(1).getStatus());
  }

  @Test
  public void testRegisterOrderAddParticipantsGetRelated() throws Exception {
    final JID client = registerUser("x@b.c");
    final JID expert = registerUser("expert@b.c");
    final JID room = JID.parse("a@b.c");
    final MySQLBoard.MySQLOrder order[] = board.register(Offer.create(
      room,
      client,
      new Message(client, room, new Message.Subject("offer"))
    ));
    order[0].role(expert, ExpLeagueOrder.Role.CANDIDATE, System.currentTimeMillis());
    order[0].role(expert, ExpLeagueOrder.Role.INVITED, System.currentTimeMillis());
    order[0].role(expert, ExpLeagueOrder.Role.ACTIVE, System.currentTimeMillis());
    final List<ExpLeagueOrder> related = board.related(expert).collect(Collectors.toList());
    assertEquals(1, related.size());
    assertEquals(order[0].room(), related.get(0).room());
  }

  @NotNull
  protected JID registerUser(final String jid) throws Exception {
    final JID client = JID.parse(jid);
    final RegisterQuery query = new RegisterQuery();
    query.username(client.local());
    query.name("Fake User: " + client.local());
    query.passwd("");
    roster.register(query);
    return client;
  }
}