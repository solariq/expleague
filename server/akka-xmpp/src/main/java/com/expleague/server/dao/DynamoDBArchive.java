package com.expleague.server.dao;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient;
import com.amazonaws.services.dynamodbv2.datamodeling.*;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.amazonaws.services.dynamodbv2.document.internal.InternalUtils;
import com.amazonaws.services.dynamodbv2.model.*;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Stanza;
import com.spbsu.commons.util.cache.CacheStrategy;
import com.spbsu.commons.util.cache.impl.FixedSizeCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * User: solar
 * Date: 21.10.15
 * Time: 14:29
 */
@SuppressWarnings("unused")
public class DynamoDBArchive implements Archive {
  private static final Logger log = Logger.getLogger(DynamoDBArchive.class.getName());
  public static final String TBTS_ROOMS = "tbts-rooms";
  private final DynamoDBMapper mapper;
  private final AmazonDynamoDBAsyncClient client;

  public DynamoDBArchive() {
    final BasicAWSCredentials credentials = new BasicAWSCredentials("AKIAJPLJBHVNFAWY3S4A", "UEnvfQ2ver5mlOu7IJsjxRH3G9uF3/f0WNLFZ9c6");
    client = new AmazonDynamoDBAsyncClient(credentials);
    mapper = new DynamoDBMapper(client);
    final List<String> tableNames = client.listTables().getTableNames();
    if (!tableNames.contains(TBTS_ROOMS)) {
      final CreateTableRequest createReq = mapper.generateCreateTableRequest(RoomArchive.class);
      createReq.setProvisionedThroughput(new ProvisionedThroughput(10L, 10L));
      client.createTable(createReq);
    }
    final DynamoDB db = new DynamoDB(client);
  }

  private FixedSizeCache<String, RoomArchive> dumpsCache = new FixedSizeCache<>(1000, CacheStrategy.Type.LRU);
  @Override
  public synchronized Dump dump(String local) {
    return dumpsCache.get(local, id -> {
      final RoomArchive archive = mapper.load(getRoomArchiveClass(), id);
      if (archive != null)
        archive.handlers(client, mapper);
      return archive;
    });
  }

  @Override
  public synchronized Dump register(String room, String owner) {
    final RoomArchive archive = new RoomArchive(room);
    archive.handlers(client, mapper);
    dumpsCache.put(room, archive);
    return archive;
  }

  protected Class<? extends RoomArchive> getRoomArchiveClass() {
    return RoomArchive.class;
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
      final Message message = new Message(stanza.from().toString(), stanza.xmlString(), System.currentTimeMillis());
      messages.add(message);
      try {
        if (messages.size() != 1) {
          client.updateItem(new UpdateItemRequest()
              .withTableName(TBTS_ROOMS)
              .withKey(InternalUtils.toAttributeValueMap(new PrimaryKey("id", id)))
              .withUpdateExpression("set #m = list_append(#m, :i)")
              .withExpressionAttributeNames(new HashMap<String, String>() {{
                put("#m", "messages");
              }})
              .withExpressionAttributeValues(new HashMap<String, AttributeValue>() {{
                put(":i", message.asMap());
              }}));
        } else mapper.save(this);
      }
      catch (ProvisionedThroughputExceededException ptee) {
        log.log(Level.WARNING, "Unable to deliver message to DynamoDB: " + ptee.getMessage());
      }
    }

    @Override
    public Stream<Stanza> stream() {
      return messages.stream().map(Message::stanza);
    }

    private JID jid;
    @Override
    public JID owner() {
      if (jid != null)
        return jid;
      for (final Message message : messages) {
        final JID jid = JID.parse(message.author);
        if (jid != null && !jid.domain().startsWith("muc."))
          return this.jid = jid;
      }
      return null;
    }

    private DynamoDBMapper mapper;
    private AmazonDynamoDB client;

    public void handlers(AmazonDynamoDB client, DynamoDBMapper mapper) {
      this.client = client;
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

    public Stanza stanza() {
      return Stanza.create(text, ts / 1000);
    }

    public String author() {
      return author;
    }

    public long timestamp() {
      return ts;
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

    public AttributeValue asMap() {
      return new AttributeValue().withL(new AttributeValue()
          .addMEntry("text", new AttributeValue().withS(text))
          .addMEntry("author", new AttributeValue().withS(author))
          .addMEntry("ts", new AttributeValue().withN(Long.toString(ts))));
    }
  }
}
