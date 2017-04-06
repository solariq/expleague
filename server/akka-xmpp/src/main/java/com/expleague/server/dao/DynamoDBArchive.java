package com.expleague.server.dao;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient;
import com.amazonaws.services.dynamodbv2.datamodeling.*;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.amazonaws.services.dynamodbv2.document.internal.InternalUtils;
import com.amazonaws.services.dynamodbv2.model.*;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.agents.XMPP;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Stanza;
import com.spbsu.commons.text.charset.TextDecoderTools;
import com.spbsu.commons.util.cache.CacheStrategy;
import com.spbsu.commons.util.cache.impl.FixedSizeCache;
import gnu.trove.list.array.TLongArrayList;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * User: solar
 * Date: 21.10.15
 * Time: 14:29
 */
@SuppressWarnings("unused")
public class DynamoDBArchive implements Archive {
  private static final Logger log = Logger.getLogger(DynamoDBArchive.class.getName());
  private final BlockingQueue<Message> roomAccumulatedChangesQueue = new LinkedBlockingQueue<>();
  private final Set<String> fullRooms = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final DynamoDBMapper mapper;
  private final AmazonDynamoDBAsyncClient client;

  public DynamoDBArchive() {
    final BasicAWSCredentials credentials = new BasicAWSCredentials("AKIAJPLJBHVNFAWY3S4A", "UEnvfQ2ver5mlOu7IJsjxRH3G9uF3/f0WNLFZ9c6");
    client = new AmazonDynamoDBAsyncClient(credentials);
    mapper = new DynamoDBMapper(client, new DynamoDBMapperConfig((DynamoDBMapperConfig.TableNameResolver) (clazz, config) -> ExpLeagueServer.config().dynamoDB()));
    final List<String> tableNames = client.listTables().getTableNames();
    final String tableName = ExpLeagueServer.config().dynamoDB();
    final long readWriteCapacity = 10L;
    if (!tableNames.contains(tableName)) {
      final CreateTableRequest createReq = mapper.generateCreateTableRequest(RoomArchive.class);
      createReq.setProvisionedThroughput(new ProvisionedThroughput(readWriteCapacity, readWriteCapacity));
      final CreateTableResult createTableResult = client.createTable(createReq);
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    final DynamoDB db = new DynamoDB(client);

    { //Start update consumer
      final Thread updateConsumer = new Thread(() -> {
        final long intervalInNanos = 1000L * 1000L * 1000L;
        final int throughputInBytesPerInterval = 1000;
        final TLongArrayList history = new TLongArrayList();
        try {
          //noinspection InfiniteLoopStatement
          while (true) {
            final Message message = roomAccumulatedChangesQueue.take();
            final String roomId = message.stanza().to().local();
            if (fullRooms.contains(roomId))
              continue;

            final long timeInNanos = System.nanoTime();
            long first = timeInNanos;
            while (!history.isEmpty()) {
              first = history.get(0);
              if (timeInNanos - first <= intervalInNanos)
                break;
              history.remove(0, 1);
            }
            if (history.size() >= readWriteCapacity) {
              final long waitTimeInNanos = intervalInNanos - (timeInNanos - first) + 10L * 1000L * 1000L;
              Thread.sleep(waitTimeInNanos / (1000L * 1000L), (int) (waitTimeInNanos % (1000L * 1000L)));
            }
            history.add(System.nanoTime());

            final List<Message> changesToApply = new ArrayList<>(Collections.singletonList(message));
            UpdateItemRequest currentUpdate = updateRequest(roomId, changesToApply);
            final Iterator<Message> queueIterator = roomAccumulatedChangesQueue.iterator();
            while (queueIterator.hasNext()) {
              final Message next = queueIterator.next();
              if (!roomId.equals(next.stanza().to().local()))
                continue;
              changesToApply.add(next);

              final UpdateItemRequest update = updateRequest(roomId, changesToApply);
              //noinspection ConstantConditions
              boolean tooBig = TextDecoderTools.getBytes(update.toString(), TextDecoderTools.UTF8).length > throughputInBytesPerInterval;
              if (tooBig) {
                changesToApply.remove(changesToApply.size() - 1);
                break;
              }
              else {
                queueIterator.remove();
                currentUpdate = update;
              }
            }

            try {
              client.updateItem(currentUpdate);
            } catch (AmazonClientException ace) {
              if (ace instanceof AmazonServiceException && ((AmazonServiceException) ace).getErrorType() == AmazonServiceException.ErrorType.Client) {
                fullRooms.add(roomId);
              }
              else {
                roomAccumulatedChangesQueue.addAll(changesToApply);
              }
              log.log(Level.WARNING, "Unable to deliver message to DynamoDB: " + ace.getMessage());
              log.log(Level.WARNING, "Failed update: " + currentUpdate.toString());
            }
          }
        } catch (InterruptedException ie) {
          log.fine("Interrupting DynamoDB saving queue");
          Thread.currentThread().interrupt();
        }
      });
      updateConsumer.setName("DynamoDB saving queue");
      updateConsumer.setDaemon(true);
      updateConsumer.start();
    }
  }

  private UpdateItemRequest updateRequest(String roomId, List<Message> changesToApply) {
    return new UpdateItemRequest()
        .withTableName(ExpLeagueServer.config().dynamoDB())
        .withKey(InternalUtils.toAttributeValueMap(new PrimaryKey("id", roomId)))
        .withUpdateExpression("set #m = list_append(#m, :i)")
        .withExpressionAttributeNames(new HashMap<String, String>() {{
          put("#m", "messages");
        }})
        .withExpressionAttributeValues(new HashMap<String, AttributeValue>() {{
          final AttributeValue itemsList = new AttributeValue();
          itemsList.setL(changesToApply.stream().map(Message::asMap).collect(Collectors.toList()));
          put(":i", itemsList);
        }});
  }

  private FixedSizeCache<String, RoomArchive> dumpsCache = new FixedSizeCache<>(1000, CacheStrategy.Type.LRU);

  @Override
  public synchronized Dump dump(String local) {
    if ("global-chat".equals(local)) {
      return new Dump() {
        private final List<Stanza> stanzas = new ArrayList<>();

        @Override
        public void accept(Stanza stanza) {
          stanzas.add(stanza);
        }

        @Override
        public void commit() {
        }

        @Override
        public Stream<Stanza> stream() {
          return stanzas.stream();
        }

        @Override
        public JID owner() {
          return XMPP.jid();
        }
      };
    }
    return dumpsCache.get(local, id -> {
      RoomArchive archive = mapper.load(getRoomArchiveClass(), id);
      if (archive == null)
        archive = new RoomArchive(id);
      archive.handlers(roomAccumulatedChangesQueue, fullRooms, mapper);
      return archive;
    });
  }

  @Override
  public synchronized Dump register(String room, String owner) {
    final RoomArchive archive = new RoomArchive(room);
    archive.handlers(roomAccumulatedChangesQueue, fullRooms, mapper);
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

    public RoomArchive() {
    }

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

    private final List<Message> accumulatedChange = new ArrayList<>();

    @Override
    public void accept(Stanza stanza) {
      if (known.contains(stanza.id()))
        return;
      final Message message = new Message(stanza.from().toString(), stanza.xmlString(), System.currentTimeMillis());
      known.add(stanza.id());
      messages.add(message);
      accumulatedChange.add(message);
    }

    @Override
    public void commit() {
      if (accumulatedChange.isEmpty())
        return;

      if (accumulatedChange.size() < messages.size()) {
        if (!fullRooms.contains(id)) {
          roomAccumulatedChangesQueue.addAll(accumulatedChange);
        }
      }
      else {
        mapper.save(this);
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
    private BlockingQueue<Message> roomAccumulatedChangesQueue;
    private Set<String> fullRooms;

    public void handlers(BlockingQueue<Message> roomAccumulatedChangesQueue, Set<String> fullRooms, DynamoDBMapper mapper) {
      this.roomAccumulatedChangesQueue = roomAccumulatedChangesQueue;
      this.fullRooms = fullRooms;
      this.mapper = mapper;
    }
  }

  @DynamoDBDocument
  public static class Message {
    private String text;
    private String author;
    private long ts;

    public Message() {
    }

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
