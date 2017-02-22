package com.expleague.server.dao;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.expleague.xmpp.stanza.Stanza;

/**
 * User: solar
 * Date: 21.10.15
 * Time: 14:29
 */
@SuppressWarnings("unused")
public class ReadOnlyDynamoDBArchive extends DynamoDBArchive {
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
      throw new UnsupportedOperationException();
    }
  }
}
