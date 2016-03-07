package com.expleague.server.dao;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient;
import com.amazonaws.services.dynamodbv2.datamodeling.*;
import com.amazonaws.services.dynamodbv2.document.AttributeUpdate;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.UpdateItemOutcome;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.expleague.server.agents.XMPP;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Stanza;
import com.spbsu.commons.util.cache.CacheStrategy;
import com.spbsu.commons.util.cache.impl.FixedSizeCache;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * User: solar
 * Date: 21.10.15
 * Time: 14:29
 */
@SuppressWarnings("unused")
public class DynamoDBArchive implements Archive {
  public static final String TBTS_ROOMS = "tbts-rooms";
  private final DynamoDBMapper mapper;
  private final Table table;

  public DynamoDBArchive() {
    final BasicAWSCredentials credentials = new BasicAWSCredentials("AKIAJPLJBHVNFAWY3S4A", "UEnvfQ2ver5mlOu7IJsjxRH3G9uF3/f0WNLFZ9c6");
    final AmazonDynamoDBAsyncClient dbClient = new AmazonDynamoDBAsyncClient(credentials);
    mapper = new DynamoDBMapper(dbClient);
    final List<String> tableNames = dbClient.listTables().getTableNames();
    if (!tableNames.contains(TBTS_ROOMS)) {
      final CreateTableRequest createReq = mapper.generateCreateTableRequest(RoomArchive.class);
      createReq.setProvisionedThroughput(new ProvisionedThroughput(10L, 10L));
      dbClient.createTable(createReq);
    }
    final DynamoDB db = new DynamoDB(dbClient);
    table = db.getTable(TBTS_ROOMS);
  }

  private FixedSizeCache<String, RoomArchive> dumpsCache = new FixedSizeCache<>(1000, CacheStrategy.Type.LRU);
  @Override
  public synchronized Dump dump(String local) {
    return dumpsCache.get(local, id -> {
      final RoomArchive archive = mapper.load(RoomArchive.class, id);
      archive.handlers(table, mapper);
      return archive;
    });
  }

  @Override
  public synchronized Dump register(String room, String owner) {
    final RoomArchive archive = new RoomArchive(room);
    archive.handlers(table, mapper);
    dumpsCache.put(room, archive);
    return archive;
  }

  @DynamoDBTable(tableName = TBTS_ROOMS)
  public static class RoomArchive implements Dump {
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

    @Override
    public void accept(Stanza stanza) {
      final Message message = new Message(stanza.from().local(), stanza.xmlString(), System.currentTimeMillis());
      messages.add(message);
      if (messages.size() != 1) {
        messages.add(message);
        UpdateItemOutcome outcome =  table.updateItem("id", id, new AttributeUpdate("messages").addElements(message));
      }
      else mapper.save(this);
    }

    @Override
    public Stream<Stanza> stream() {
      return messages.stream().map(msg -> (Stanza)Stanza.create(msg.text));
    }

    @Override
    public JID owner() {
      return XMPP.jid(messages.get(0).author);
    }

    private Table table;
    private DynamoDBMapper mapper;

    public void handlers(Table table, DynamoDBMapper mapper) {
      this.table = table;
      this.mapper = mapper;
    }
  }

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
