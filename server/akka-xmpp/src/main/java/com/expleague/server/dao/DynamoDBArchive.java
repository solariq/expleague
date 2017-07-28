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
import com.google.common.collect.Lists;
import com.spbsu.commons.text.charset.TextDecoderTools;
import com.spbsu.commons.util.cache.CacheStrategy;
import com.spbsu.commons.util.cache.impl.FixedSizeCache;
import gnu.trove.list.array.TLongArrayList;

import java.util.*;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingDeque;
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
  private static final int ROOM_POSTFIX_START = 1;

  private final BlockingDeque<Message> roomAccumulatedChangesDeque = new LinkedBlockingDeque<>();
  private final ConcurrentHashMap<String, Integer> roomPostfix = new ConcurrentHashMap<>();
  private final DynamoDBMapper mainTableMapper;
  private final AmazonDynamoDBAsyncClient client;
  private final ConcurrentMap<String, String> lastMessages = new ConcurrentHashMap<>();

  public DynamoDBArchive() {
    final BasicAWSCredentials credentials = new BasicAWSCredentials("AKIAJPLJBHVNFAWY3S4A", "UEnvfQ2ver5mlOu7IJsjxRH3G9uF3/f0WNLFZ9c6");
    //noinspection deprecation
    client = new AmazonDynamoDBAsyncClient(credentials);
    final List<String> tableNames = client.listTables().getTableNames();
    final long readWriteCapacity = 10L;

    final String mainTableName = ExpLeagueServer.config().dynamoDB();
    //noinspection deprecation
    mainTableMapper = new DynamoDBMapper(client, new DynamoDBMapperConfig((DynamoDBMapperConfig.TableNameResolver) (clazz, config) -> mainTableName));
    if (!tableNames.contains(mainTableName)) {
      createTable(mainTableMapper, RoomArchive.class, readWriteCapacity, readWriteCapacity);
    }
    final String lastMessageTableName = ExpLeagueServer.config().dynamoDBLastMessages();
    //noinspection deprecation
    final DynamoDBMapper lastMessagesMapper = new DynamoDBMapper(client, new DynamoDBMapperConfig((DynamoDBMapperConfig.TableNameResolver) (clazz, config) -> lastMessageTableName));
    if (!tableNames.contains(lastMessageTableName)) {
      createTable(lastMessagesMapper, RoomLastMessage.class, readWriteCapacity, readWriteCapacity);
    }
    else {
      final PaginatedScanList<RoomLastMessage> scanList = lastMessagesMapper.scan(RoomLastMessage.class, new DynamoDBScanExpression());
      scanList.forEach(roomLastMessage -> lastMessages.put(roomLastMessage.getRoom(), roomLastMessage.getMessageId()));
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
            final Message message = roomAccumulatedChangesDeque.take();
            final String roomId = message.stanza().to().local();
            final String dynamoDbItemId = roomPostfix.containsKey(roomId) ? itemId(roomId, roomPostfix.get(roomId)) : roomId;

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
            UpdateItemRequest currentUpdate = updateRequest(dynamoDbItemId, changesToApply);
            final Iterator<Message> queueIterator = roomAccumulatedChangesDeque.iterator();
            while (queueIterator.hasNext()) {
              final Message next = queueIterator.next();
              if (!roomId.equals(next.stanza().to().local()))
                continue;
              changesToApply.add(next);

              final UpdateItemRequest update = updateRequest(dynamoDbItemId, changesToApply);
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

            boolean mainTableUpdateFails = false;
            try {
              final String lastMessageId = changesToApply.get(changesToApply.size() - 1).stanza().id();
              final UpdateItemRequest updateLastMessage = new UpdateItemRequest()
                  .withTableName(lastMessageTableName)
                  .withKey(InternalUtils.toAttributeValueMap(new PrimaryKey("room", roomId)))
                  .addAttributeUpdatesEntry("messageId", new AttributeValueUpdate().withValue(new AttributeValue().withS(lastMessageId)).withAction(AttributeAction.PUT));
              client.updateItem(updateLastMessage);
              mainTableUpdateFails = true;
              client.updateItem(currentUpdate);
            } catch (AmazonClientException ace) {
              if (mainTableUpdateFails && ace instanceof AmazonServiceException && ((AmazonServiceException) ace).getErrorType() == AmazonServiceException.ErrorType.Client) {
                roomPostfix.compute(roomId, (id, postfix) -> postfix == null ? ROOM_POSTFIX_START : postfix + 1);
              }
              Lists.reverse(changesToApply).forEach(roomAccumulatedChangesDeque::addFirst);
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

  private UpdateItemRequest updateRequest(String id, List<Message> changesToApply) {
    return new UpdateItemRequest()
        .withTableName(ExpLeagueServer.config().dynamoDB())
        .withKey(InternalUtils.toAttributeValueMap(new PrimaryKey("id", id)))
        .withUpdateExpression("set #m = list_append(if_not_exists(#m, :empty_list), :i)")
        .withExpressionAttributeNames(new HashMap<String, String>() {{
          put("#m", "messages");
        }})
        .withExpressionAttributeValues(new HashMap<String, AttributeValue>() {{
          final AttributeValue itemsList = new AttributeValue();
          itemsList.setL(changesToApply.stream().map(Message::asMap).collect(Collectors.toList()));
          put(":i", itemsList);

          final AttributeValue emptyList = new AttributeValue();
          emptyList.setL(Collections.emptyList());
          put(":empty_list", emptyList);
        }});
  }

  private String itemId(String id, int num) {
    return id + "#" + num;
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

        @Override
        public int size() {
          return stanzas.size();
        }
      };
    }
    return dumpsCache.get(local, id -> {
      RoomArchive archive = mainTableMapper.load(getRoomArchiveClass(), id);
      if (archive == null)
        archive = new RoomArchive(id);

      for (int i = ROOM_POSTFIX_START; i < Integer.MAX_VALUE; i++) {
        final String extraItemId = itemId(id, i);
        final RoomArchive extraArchive = mainTableMapper.load(getRoomArchiveClass(), extraItemId);
        if (extraArchive == null) {
          final int postfix = i - 1;
          if (postfix >= ROOM_POSTFIX_START) {
            roomPostfix.putIfAbsent(id, postfix);
          }
          break;
        }
        else {
          archive.setMessages(extraArchive.getMessages());
        }
      }

      archive.handlers(roomAccumulatedChangesDeque, lastMessages);
      return archive;
    });
  }

  @Override
  public synchronized Dump register(String room, String owner) {
    final RoomArchive archive = new RoomArchive(room);
    archive.handlers(roomAccumulatedChangesDeque, lastMessages);
    dumpsCache.put(room, archive);
    return archive;
  }

  @Override
  public String lastMessageId(String local) {
    return lastMessages.get(local);
  }

  protected Class<? extends RoomArchive> getRoomArchiveClass() {
    return RoomArchive.class;
  }

  private void createTable(DynamoDBMapper mapper, Class<?> clazz, long readCapacity, long writeCapacity) {
    final CreateTableRequest createReq = mapper.generateCreateTableRequest(clazz);
    createReq.setProvisionedThroughput(new ProvisionedThroughput(readCapacity, writeCapacity));
    final CreateTableResult createTableResult = client.createTable(createReq);
    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @DynamoDBTable(tableName = "expleague-rooms")
  public static class RoomArchive implements Dump {
    String id;
    final Set<String> known = new HashSet<>();
    final List<Message> messages = new ArrayList<>();

    public RoomArchive() {
    }

    public RoomArchive(String id) {
      this.id = id;
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

    public synchronized void setMessages(List<Message> messages) {
      for (Message message : messages) {
        final Stanza stanza = message.stanza();
        if (known.contains(stanza.id()) || known.contains(stanza.strippedVitalikId()))
          continue;
        known.add(stanza.id());
        this.messages.add(message);
      }
    }

    private final List<Message> accumulatedChange = new ArrayList<>();

    @Override
    public synchronized void accept(Stanza stanza) {
      if (known.contains(stanza.id()) || known.contains(stanza.strippedVitalikId()))
        return;
      final Message message = new Message(stanza.from().toString(), stanza.xmlString(), System.currentTimeMillis());
      known.add(stanza.id());
      messages.add(message);
      lastMessages.put(id, stanza.id());
      accumulatedChange.add(message);
    }

    @Override
    public void commit() {
      if (accumulatedChange.isEmpty())
        return;

      roomAccumulatedChangesDeque.addAll(accumulatedChange);
      accumulatedChange.clear();
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

    @Override
    public int size() {
      return messages.size();
    }

    private BlockingDeque<Message> roomAccumulatedChangesDeque;
    private ConcurrentMap<String, String> lastMessages;

    public void handlers(BlockingDeque<Message> roomAccumulatedChangesQueue, ConcurrentMap<String, String> lastMessages) {
      this.roomAccumulatedChangesDeque = roomAccumulatedChangesQueue;
      this.lastMessages = lastMessages;
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
      return Stanza.create(text);
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

  @DynamoDBTable(tableName = "expleague-rooms-last-messages")
  public static class RoomLastMessage {
    String room;
    String messageId;

    @DynamoDBHashKey
    public String getRoom() {
      return room;
    }

    public void setRoom(String room) {
      this.room = room;
    }

    @DynamoDBAttribute
    public String getMessageId() {
      return messageId;
    }

    public void setMessageId(String messageId) {
      this.messageId = messageId;
    }
  }
}