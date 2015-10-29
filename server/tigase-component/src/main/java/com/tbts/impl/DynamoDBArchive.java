package com.tbts.impl;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient;
import com.amazonaws.services.dynamodbv2.datamodeling.*;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.tbts.model.handlers.Archive;
import com.tbts.model.Client;
import com.tbts.model.Room;
import com.tbts.model.impl.ClientImpl;
import com.tbts.model.impl.RoomImpl;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * User: solar
 * Date: 21.10.15
 * Time: 14:29
 */
public class DynamoDBArchive extends Archive {
  public static final String TBTS_ROOMS = "tbts-rooms";
  private final DynamoDBMapper mapper;

  public DynamoDBArchive() {
    final BasicAWSCredentials credentials = new BasicAWSCredentials("AKIAJPLJBHVNFAWY3S4A", "UEnvfQ2ver5mlOu7IJsjxRH3G9uF3/f0WNLFZ9c6");
    final AmazonDynamoDBAsyncClient db = new AmazonDynamoDBAsyncClient(credentials);
    mapper = new DynamoDBMapper(db);
    final List<String> tableNames = db.listTables().getTableNames();
    if (!tableNames.contains(TBTS_ROOMS)) {
      final CreateTableRequest createReq = mapper.generateCreateTableRequest(RoomArchive.class);
      createReq.setProvisionedThroughput(new ProvisionedThroughput(100l, 100l));
      db.createTable(createReq);
    }
  }

  @Override
  public void log(Room room, String authorId, CharSequence element) {
    RoomArchive archive = mapper.load(RoomArchive.class, room.id());
    if (archive == null)
      archive = new RoomArchive(room.id());
    archive.log(element, authorId);
    mapper.save(archive);
  }

  @Override
  public void visitMessages(Room room, MessageVisitor visitor) {
    final RoomArchive archive = mapper.load(RoomArchive.class, room.id());
    if (archive != null) {
      for (final Message message : archive.getMessages()) {
        if (!visitor.accept(message.author, message.text, message.ts))
          break;
      }
    }
  }

  @Test
  public void testSave() {
    Archive archive = new DynamoDBArchive();
    Client client = new ClientImpl("client@localhost");
    archive.log(new RoomImpl("room@muc.localhost", client), client.id(), "Hello world");
  }


  @SuppressWarnings("unused")
  @DynamoDBTable(tableName = "tbts-rooms")
  public static class RoomArchive {
    String id;
    List<Message> messages;

    public RoomArchive() {}

    public RoomArchive(String id) {
      this.id = id;
      messages = new ArrayList<>();
    }

    @DynamoDBHashKey
    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    @DynamoDBAttribute
    public List<Message> getMessages() {
      return messages;
    }

    public void setMessages(List<Message> messages) {
      this.messages = messages;
    }

    public void log(CharSequence message, String authorId) {
      messages.add(new Message(authorId, message, System.currentTimeMillis()));
    }
  }

  @SuppressWarnings("unused")
  @DynamoDBDocument
  public static class Message {
    private String text;
    private String author;
    private long ts;

    public Message() {}

    public Message(String authorId, CharSequence message, long ts) {
      this.author = authorId;
      this.text = message.toString();
      this.ts = ts;
    }

    @DynamoDBAttribute
    public String getText() {
      return text;
    }
    public void setText(String text) {
      this.text = text;
    }

    @DynamoDBAttribute
    public String getAuthor() {
      return author;
    }
    public void setAuthor(String author) {
      this.author = author;
    }

    @DynamoDBAttribute
    public long getTs() {
      return ts;
    }

    public void setTs(long ts) {
      this.ts = ts;
    }
  }

}
