package com.expleague.server.dao;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.expleague.xmpp.stanza.Stanza;

import java.util.logging.Logger;

/**
 * User: solar
 * Date: 21.10.15
 * Time: 14:29
 */
@SuppressWarnings("unused")
public class ReadOnlyDynamoDBArchive extends DynamoDBArchive {
  private static final Logger log = Logger.getLogger(ReadOnlyDynamoDBArchive.class.getName());
  @Override
  protected Class<? extends RoomArchive> getRoomArchiveClass() {
    return ReadOnlyRoomArchive.class;
  }

  @Override
  public synchronized Dump register(String room, String owner) {
    throw new UnsupportedOperationException();
  }

  @DynamoDBTable(tableName = "expleague-rooms")
  public static class ReadOnlyRoomArchive extends RoomArchive {
    public ReadOnlyRoomArchive() {}

    public ReadOnlyRoomArchive(String id) {
      super(id);
    }

    @Override
    public void accept(Stanza stanza) {
      if (!known.contains(stanza.id()))
        log.fine("Read only archive skipped message: " + stanza.xmlString());
//        throw new UnsupportedOperationException();
    }
  }
}
