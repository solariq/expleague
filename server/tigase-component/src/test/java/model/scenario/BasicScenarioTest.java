package model.scenario;

import com.spbsu.commons.filters.TrueFilter;
import com.spbsu.commons.func.Action;
import com.tbts.model.Client;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import com.tbts.model.handlers.*;
import model.scenario.fake.ObedientClient;
import model.scenario.fake.ObedientExpert;
import org.junit.*;
import tigase.util.TigaseStringprepException;

import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:39
 */
public class BasicScenarioTest {

  private static DAO initialDAO;
  private static MyDAO myDAO;
  private static Archive initialArchive;
  private static InMemArchive myArchive;

  @BeforeClass
  public static void setUp() {
    initialDAO = DAO.instance;
    initialArchive = Archive.instance;
    DAO.instance = myDAO = new MyDAO();
    Archive.instance = myArchive = new InMemArchive();
  }

  @AfterClass
  public static void tearDown() {
    DAO.instance = initialDAO;
    Archive.instance = initialArchive;
  }

  @After
  public void clearManagers() {
    myDAO.clear();
    myArchive.clear();
  }

  @Test
  public void testShortSuccess() throws TigaseStringprepException, InterruptedException {
    final StringBuffer track = new StringBuffer();
    final StatusTracker tracker = new StatusTracker(track);
    final Client client = ClientManager.instance().get("client@localhost");
    client.addListener(tracker.clientListener());
    Reception.instance().addListener(tracker.roomListener());

    final Expert expert = ExpertManager.instance().register("expert@localhost");
    expert.addListener(tracker.expertListener());


    expert.online(true);
    client.presence(true);
    final Room room = Reception.instance().room(client, "room@muc.localhost");
    room.open();
    client.activate(room);
    client.formulating();
    client.query();
    Thread.sleep(200);

    Assert.assertEquals(
            "Expert expert@localhost -> READY\n" +
            "Client client@localhost -> ONLINE\n" +
            "MyRoom room@muc.localhost -> INIT\n" +
            "MyRoom room@muc.localhost -> CLEAN\n" +
            "Client client@localhost -> FORMULATING\n" +
            "Client client@localhost -> COMMITED\n" +
            "MyRoom room@muc.localhost -> DEPLOYED\n" +
            "Expert expert@localhost -> CHECK\n" +
            "Expert expert@localhost -> STEADY\n" +
            "Expert expert@localhost -> INVITE\n" +
            "Expert expert@localhost -> GO\n" +
            "MyRoom room@muc.localhost -> LOCKED\n" +
            "MyRoom room@muc.localhost -> COMPLETE\n" +
            "Client client@localhost -> FEEDBACK\n" +
            "Client client@localhost -> ONLINE\n" +
            "Expert expert@localhost -> READY\n", track.toString());
  }

  @Test
  public void testExpertAfterClient() throws TigaseStringprepException, InterruptedException {
    final StringBuffer track = new StringBuffer();
    final StatusTracker tracker = new StatusTracker(track);
    final Client client = ClientManager.instance().get("client@localhost");
    client.addListener(tracker.clientListener());
    Reception.instance().addListener(tracker.roomListener());

    final Expert expert = ExpertManager.instance().register("expert@localhost");
    expert.addListener(tracker.expertListener());

    client.presence(true);
    final Room room = Reception.instance().room(client, "room@muc.localhost");
    room.open();
    client.activate(room);
    client.formulating();
    client.query();

    expert.online(true);

    Thread.sleep(200);

    Assert.assertEquals(
        "Client client@localhost -> ONLINE\n" +
        "MyRoom room@muc.localhost -> INIT\n" +
        "MyRoom room@muc.localhost -> CLEAN\n" +
        "Client client@localhost -> FORMULATING\n" +
        "Client client@localhost -> COMMITED\n" +
        "MyRoom room@muc.localhost -> DEPLOYED\n" +
        "Expert expert@localhost -> READY\n" +
        "Expert expert@localhost -> CHECK\n" +
        "Expert expert@localhost -> STEADY\n" +
        "Expert expert@localhost -> INVITE\n" +
        "Expert expert@localhost -> GO\n" +
        "MyRoom room@muc.localhost -> LOCKED\n" +
        "MyRoom room@muc.localhost -> COMPLETE\n" +
        "Client client@localhost -> FEEDBACK\n" +
        "Client client@localhost -> ONLINE\n" +
        "Expert expert@localhost -> READY\n", track.toString());
  }

  @Test
  public void testChatSuccess() throws TigaseStringprepException, InterruptedException {
    final StringBuffer track = new StringBuffer();
    final StatusTracker tracker = new StatusTracker(track);
    final Client client = ClientManager.instance().get("client-chat-1@localhost");
    client.addListener(tracker.clientListener());
    Reception.instance().addListener(tracker.roomListener());

    final Expert expert = ExpertManager.instance().register("expert@localhost");
    expert.addListener(tracker.expertListener());

    expert.online(true);

    client.presence(true);
    final Room room = Reception.instance().room(client, "room@muc.localhost");
    client.activate(room);
    room.open();
    client.formulating();
    client.query();
    Thread.sleep(200);

    Assert.assertEquals(
        "Expert expert@localhost -> READY\n" +
        "Client client@localhost -> ONLINE\n" +
        "MyRoom room@muc.localhost -> INIT\n" +
        "MyRoom room@muc.localhost -> CLEAN\n" +
        "Client client@localhost -> FORMULATING\n" +
        "Client client@localhost -> COMMITED\n" +
        "MyRoom room@muc.localhost -> DEPLOYED\n" +
        "Expert expert@localhost -> CHECK\n" +
        "Expert expert@localhost -> STEADY\n" +
        "Expert expert@localhost -> INVITE\n" +
        "Expert expert@localhost -> GO\n" +
        "MyRoom room@muc.localhost -> LOCKED\n" +
        "MyRoom room@muc.localhost -> COMPLETE\n" +
        "Client client@localhost -> FEEDBACK\n" +
        "Client client@localhost -> FORMULATING\n" +
        "Client client@localhost -> COMMITED\n" +
        "MyRoom room@muc.localhost -> DEPLOYED\n" +
        "Expert expert@localhost -> READY\n" +
        "Expert expert@localhost -> CHECK\n" +
        "Expert expert@localhost -> STEADY\n" +
        "Expert expert@localhost -> INVITE\n" +
        "Expert expert@localhost -> GO\n" +
        "MyRoom room@muc.localhost -> LOCKED\n" +
        "MyRoom room@muc.localhost -> COMPLETE\n" +
        "Client client@localhost -> FEEDBACK\n" +
        "Client client@localhost -> ONLINE\n" +
        "Expert expert@localhost -> READY\n", track.toString());
  }

  @Test
  public void testChat2Success() throws TigaseStringprepException, InterruptedException {
    final StringBuffer track = new StringBuffer();
    final StatusTracker tracker = new StatusTracker(track);
    final Client client = ClientManager.instance().get("client-chat-2@localhost");
    client.addListener(tracker.clientListener());
    Reception.instance().addListener(tracker.roomListener());

    final Expert expert = ExpertManager.instance().register("expert@localhost");
    expert.addListener(tracker.expertListener());

    expert.online(true);

    client.presence(true);
    final Room room = Reception.instance().room(client, "room@muc.localhost");
    client.activate(room);
    room.open();
    client.formulating();
    client.query();
    Thread.sleep(200);

    Assert.assertEquals(
        "Expert expert@localhost -> READY\n" +
        "Client client@localhost -> ONLINE\n" +
        "MyRoom room@muc.localhost -> INIT\n" +
        "MyRoom room@muc.localhost -> CLEAN\n" +
        "Client client@localhost -> FORMULATING\n" +
        "Client client@localhost -> COMMITED\n" +
        "MyRoom room@muc.localhost -> DEPLOYED\n" +
        "Expert expert@localhost -> CHECK\n" +
        "Expert expert@localhost -> STEADY\n" +
        "Expert expert@localhost -> INVITE\n" +
        "Expert expert@localhost -> GO\n" +
        "MyRoom room@muc.localhost -> LOCKED\n" +
        "MyRoom room@muc.localhost -> COMPLETE\n" +
        "Client client@localhost -> FEEDBACK\n" +
        "Client client@localhost -> FORMULATING\n" +
        "Client client@localhost -> COMMITED\n" +
        "MyRoom room@muc.localhost -> DEPLOYED\n" +
        "Expert expert@localhost -> READY\n" +
        "Expert expert@localhost -> CHECK\n" +
        "Expert expert@localhost -> STEADY\n" +
        "Expert expert@localhost -> INVITE\n" +
        "Expert expert@localhost -> GO\n" +
        "MyRoom room@muc.localhost -> LOCKED\n" +
        "MyRoom room@muc.localhost -> COMPLETE\n" +
        "Client client@localhost -> FEEDBACK\n" +
        "Client client@localhost -> FORMULATING\n" +
        "Client client@localhost -> COMMITED\n" +
        "MyRoom room@muc.localhost -> DEPLOYED\n" +
        "Expert expert@localhost -> READY\n" +
        "Expert expert@localhost -> CHECK\n" +
        "Expert expert@localhost -> STEADY\n" +
        "Expert expert@localhost -> INVITE\n" +
        "Expert expert@localhost -> GO\n" +
        "MyRoom room@muc.localhost -> LOCKED\n" +
        "MyRoom room@muc.localhost -> COMPLETE\n" +
        "Client client@localhost -> FEEDBACK\n" +
        "Client client@localhost -> ONLINE\n" +
        "Expert expert@localhost -> READY\n", track.toString());
  }

  @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
  public static class StatusTracker {
    private final StringBuffer buffer;
    private Action<Client> clientListener  = new Action<Client>() {
      public void invoke(Client client) {
        buffer.append("Client " + client.id() + " -> " + client.state().toString() + "\n");
      }
    };
    private Action<Expert> expertListener  = new Action<Expert>() {
      public void invoke(Expert expert) {
        buffer.append("Expert " + expert.id() + " -> " + expert.state().toString() + "\n");
      }
    };

    private Action<Room> roomListener  = new Action<Room>() {
      public void invoke(Room room) {
        buffer.append("MyRoom " + room.id() + " -> " + room.state().toString() + "\n");
      }
    };

    public StatusTracker(StringBuffer builder) {
      this.buffer = builder;
    }

    public Action<Client> clientListener() {
      return clientListener;
    }

    public Action<Expert> expertListener() {
      return expertListener;
    }

    public Action<Room> roomListener() {
      return roomListener;
    }
  }

  private static class MyDAO extends DAO {
    protected MyDAO() {
      super(new TrueFilter<>());
    }

    @Override
    public Client createClient(String id) {
      if (id.contains("chat")) {
        try {
          Object[] fields = new MessageFormat("{1}-chat-{0,number,integer}@{2}").parse(id);
          final long count = (Long) fields[0];
          return new ObedientClient(fields[1] + "@" + fields[2], (int)count);
        } catch (ParseException e) {
          throw new RuntimeException(e);
        }
      }
      final ObedientClient client = new ObedientClient(id);
      clientsMap.put(id, client);
      return client;
    }

    @Override
    public Expert createExpert(String id) {
      final ObedientExpert expert = new ObedientExpert(id);
      expertsMap.put(id, expert);
      return expert;
    }

    public void clear() {
      clientsMap.clear();
      expertsMap.clear();
      roomsMap.clear();
    }
  }

  private static class InMemArchive extends Archive {
    final Map<String, List<Msg>> map = new HashMap<>();

    @Override
    public void log(Room room, String authorId, CharSequence element) {
      List<Msg> msgs = map.get(room.id());
      if (msgs == null)
        map.put(room.id(), msgs = new ArrayList<>());
      msgs.add(new Msg(authorId, element, System.currentTimeMillis()));
    }

    @Override
    public void visitMessages(Room room, MessageVisitor visitor) {
      final List<Msg> msgs = map.get(room.id());
      if (msgs != null) {
        for (final Msg msg : msgs) {
          visitor.accept(msg.author, msg.message, msg.ts);
        }
      }
    }

    public void clear() {
      map.clear();
    }

    class Msg {
      String author;
      long ts;
      CharSequence message;

      public Msg(String author, CharSequence message, long ts) {
        this.author = author;
        this.message = message;
        this.ts = ts;
      }
    }
  }
}
