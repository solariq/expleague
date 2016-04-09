package com.expleague.server.dao.sql;

import com.expleague.model.Offer;
import com.expleague.server.ExpLeagueServerTestCase;
import com.expleague.server.agents.ExpLeagueOrder;
import com.expleague.server.agents.LaborExchange;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.register.RegisterQuery;
import com.expleague.xmpp.stanza.Message;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
    final MySQLBoard.MySQLOrder order = board.register(Offer.create(
      room,
      client,
      new Message(client, room, new Message.Subject("offer"))
    ));
    order.tag("tag");
    assertTrue(Arrays.asList(order.tags()).contains("tag"));
  }

  @Test
  public void testRegisterOrderReplayAddTag() throws Exception {
    final JID client = registerUser("x@b.c");
    final JID room = JID.parse("a@b.c");
    final MySQLBoard.MySQLOrder order = board.register(Offer.create(
      room,
      client,
      new Message(client, room, new Message.Subject("offer"))
    ));
    board.open().collect(Collectors.toList());
    order.tag("tag");
    assertTrue(Arrays.asList(order.tags()).contains("tag"));
  }

  @Test
  public void testRegisterOrderSetStatusReplayAndCheck() throws Exception {
    final JID client = registerUser("x@b.c");
    final JID room = JID.parse("a@b.c");
    final MySQLBoard.MySQLOrder order = board.register(Offer.create(
      room,
      client,
      new Message(client, room, new Message.Subject("offer"))
    ));
    order.status(ExpLeagueOrder.Status.DONE);
    final ExpLeagueOrder expLeagueOrder = board.orders(new LaborExchange.OrderFilter(false, EnumSet.allOf(ExpLeagueOrder.Status.class))).findFirst().get();
    assertEquals(ExpLeagueOrder.Status.DONE, expLeagueOrder.status());
    final List<ExpLeagueOrder.StatusHistoryRecord> statusHistoryRecords = expLeagueOrder.statusHistoryRecords().collect(Collectors.toList());
    assertEquals(2, statusHistoryRecords.size());
    assertEquals(ExpLeagueOrder.Status.OPEN, statusHistoryRecords.get(0).getStatus());
    assertEquals(ExpLeagueOrder.Status.DONE, statusHistoryRecords.get(1).getStatus());
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