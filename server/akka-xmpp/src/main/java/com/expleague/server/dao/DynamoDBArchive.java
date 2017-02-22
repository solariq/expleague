package com.expleague.server.dao;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient;
import com.amazonaws.services.dynamodbv2.datamodeling.*;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.amazonaws.services.dynamodbv2.document.internal.InternalUtils;
import com.amazonaws.services.dynamodbv2.model.*;
import com.expleague.server.ExpLeagueServer;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.expleague.xmpp.stanza.Stanza;
import com.spbsu.commons.util.cache.CacheStrategy;
import com.spbsu.commons.util.cache.impl.FixedSizeCache;

import java.util.*;
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
  private final DynamoDBMapper mapper;
  private final AmazonDynamoDBAsyncClient client;

  public DynamoDBArchive() {
    final BasicAWSCredentials credentials = new BasicAWSCredentials("AKIAJPLJBHVNFAWY3S4A", "UEnvfQ2ver5mlOu7IJsjxRH3G9uF3/f0WNLFZ9c6");
    client = new AmazonDynamoDBAsyncClient(credentials);
    mapper = new DynamoDBMapper(client, new DynamoDBMapperConfig((DynamoDBMapperConfig.TableNameResolver) (clazz, config) -> ExpLeagueServer.config().dynamoDB()));
    final List<String> tableNames = client.listTables().getTableNames();
    final String tableName = ExpLeagueServer.config().dynamoDB();
    if (!tableNames.contains(tableName)) {
      final CreateTableRequest createReq = mapper.generateCreateTableRequest(RoomArchive.class);
      createReq.setProvisionedThroughput(new ProvisionedThroughput(10L, 10L));
      final CreateTableResult createTableResult = client.createTable(createReq);
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    final DynamoDB db = new DynamoDB(client);
  }

  private FixedSizeCache<String, RoomArchive> dumpsCache = new FixedSizeCache<>(1000, CacheStrategy.Type.LRU);
  @Override
  public synchronized Dump dump(String local) {
    return dumpsCache.get(local, id -> {
      RoomArchive archive = mapper.load(getRoomArchiveClass(), id);
      if (archive == null)
        archive = new RoomArchive(id);
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

  @DynamoDBTable(tableName = "expleague-rooms")
  public static class RoomArchive implements Dump {
    String id;
    Set<String> known = new HashSet<>();
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
      messages.forEach(m -> known.add(m.stanza().id()));
    }

    public void log(CharSequence message, String authorId) {
      messages.add(new Message(authorId, message, System.currentTimeMillis()));
    }

    private final List<AttributeValue> accumulatedChange = new ArrayList<>();
    @Override
    public void accept(Stanza stanza) {
      if (known.contains(stanza.id()))
        return;
      final Message message = new Message(stanza.from().toString(), stanza.xmlString(), System.currentTimeMillis());
      known.add(stanza.id());
      messages.add(message);
      final AttributeValue value = message.asMap();
      accumulatedChange.add(value);
    }

    @Override
    public void commit() {
      if (accumulatedChange.isEmpty())
        return;
      final StringBuilder expressionBuilder = new StringBuilder();
      try {
        if (accumulatedChange.size() < messages.size()) {
          expressionBuilder.append("set ");
          for (int i = 0; i < accumulatedChange.size(); i++) {
            if (i > 0)
              expressionBuilder.append(", ");
            expressionBuilder.append("messages[").append(i + messages.size()).append("]=:i[").append(i).append("]");
          }

          final UpdateItemRequest update = new UpdateItemRequest()
              .withTableName(ExpLeagueServer.config().dynamoDB())
              .withKey(InternalUtils.toAttributeValueMap(new PrimaryKey("id", id)))
              .withUpdateExpression("set #m = list_append(#m, :i)")
              .withExpressionAttributeNames(new HashMap<String, String>() {{
                put("#m", "messages");
              }})
              .withExpressionAttributeValues(new HashMap<String, AttributeValue>() {{
                final AttributeValue itemsList = new AttributeValue();
                itemsList.setL(accumulatedChange);
                put(":i", itemsList);
              }});

          client.updateItem(update);
        }
        else mapper.save(this);
      }
      catch (AmazonClientException ace) {
        log.log(Level.WARNING, "Unable to deliver message to DynamoDB: " + ace.getMessage());
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
      return new AttributeValue()
          .addMEntry("text", new AttributeValue().withS(text))
          .addMEntry("author", new AttributeValue().withS(author))
          .addMEntry("ts", new AttributeValue().withN(Long.toString(ts)));
    }
  }
}
