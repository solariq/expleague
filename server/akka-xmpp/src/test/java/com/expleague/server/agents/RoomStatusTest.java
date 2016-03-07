package com.expleague.server.agents;

import com.expleague.model.Offer;
import com.expleague.model.Operations;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author vpdelta
 */
public class RoomStatusTest extends ActorSystemTestCase {
  private final JID client = JID.parse("client@expleague.com");
  private final JID room = JID.parse("room@expleague.com");
  private final JID expert = JID.parse("expert@expleague.com");

//  @Test
//  public void testStatusInitialization() throws Exception {
//    final ExpLeagueRoomAgent.Status status = new ExpLeagueRoomAgent.Status();
//    assertFalse(status.isOpen());
//    assertFalse(status.isLastWorkerActive());
//    assertFalse(status.isParticipant(client));
//    assertFalse(status.isWorker(room));
//    assertNull(status.offer());
//  }
//
//  @Test
//  public void testAcceptCreate() throws Exception {
//    final ExpLeagueRoomAgent.Status status = new ExpLeagueRoomAgent.Status();
//    status.accept(new Message(client, room, new Operations.Create()));
//    assertTrue(status.isOpen());
//    assertNull(status.offer());
//  }
//
//  @Test
//  public void testAcceptOffer() throws Exception {
//    final ExpLeagueRoomAgent.Status status = new ExpLeagueRoomAgent.Status();
//    status.accept(new Message(client, room, new Operations.Create()));
//    final Message.Subject subject = new Message.Subject("subj");
//    status.accept(new Message(client, room, subject));
//    assertTrue(status.isOpen());
//    final Offer offer = status.offer();
//    assertNotNull(offer);
//    assertEquals(room.bare(), offer.room());
//    assertEquals(client.bare(), offer.client());
//    assertEquals(client.bare(), status.owner());
//    assertTrue(status.isParticipant(client));
//  }
//
////  @Test todo
//  public void testAcceptOfferThanStart() throws Exception {
//    final ExpLeagueRoomAgent.Status status = new ExpLeagueRoomAgent.Status();
//    status.accept(new Message(client, room, new Operations.Create()));
//    final Message.Subject subject = new Message.Subject("subj");
//    status.accept(new Message(client, room, subject));
//    status.accept(new Message(expert, room, new Operations.Start()));
//    assertNotNull(status.offer());
//    assertEquals(expert, status.lastWorker());
//    assertTrue(status.isLastWorkerActive());
//    assertTrue(status.isOpen());
//    assertTrue(status.isParticipant(client));
//    assertTrue(status.isParticipant(expert));
//  }
}