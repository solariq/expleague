package model.scenario;

import com.spbsu.commons.func.Action;
import com.tbts.model.Client;
import com.tbts.model.Expert;
import com.tbts.model.Reception;
import com.tbts.model.Room;
import com.tbts.model.experts.ExpertManager;
import model.scenario.fake.ObedientClient;
import model.scenario.fake.ObedientExpert;
import org.junit.Assert;
import org.junit.Test;
import tigase.util.TigaseStringprepException;
import tigase.xmpp.BareJID;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:39
 */
public class BasicScenarioTest {

  @Test
  public void testShortSuccess() throws TigaseStringprepException, InterruptedException {
    Reception.instance().clear();
    final StringBuffer track = new StringBuffer();
    final StatusTracker tracker = new StatusTracker(track);
    final ObedientExpert expert = new ObedientExpert();
    expert.addListener(tracker.expertListener());
    final ObedientClient client = new ObedientClient();
    client.addListener(tracker.clientListener());
    Reception.instance().addListener(tracker.roomListener());

    ExpertManager.instance().register(expert);
    expert.online(true);

    client.presence(true);
    final Room room = Reception.instance().room(client, BareJID.bareJIDInstance("room@muc.localhost"));
    room.open();
    client.activate(room);
    client.formulating();
    client.query();
    Thread.sleep(200);

    Assert.assertEquals(
            "Expert expert@localhost -> READY\n" +
            "Client client@localhost -> ONLINE\n" +
            "Room room@muc.localhost -> INIT\n" +
            "Room room@muc.localhost -> CLEAN\n" +
            "Client client@localhost -> FORMULATING\n" +
            "Client client@localhost -> COMMITED\n" +
            "Room room@muc.localhost -> DEPLOYED\n" +
            "Expert expert@localhost -> CHECK\n" +
            "Expert expert@localhost -> STEADY\n" +
            "Expert expert@localhost -> INVITE\n" +
            "Expert expert@localhost -> GO\n" +
            "Room room@muc.localhost -> LOCKED\n" +
            "Expert expert@localhost -> READY\n" +
            "Room room@muc.localhost -> COMPLETE\n" +
            "Client client@localhost -> FEEDBACK\n" +
            "Client client@localhost -> ONLINE\n", track.toString());
  }

  @Test
  public void testExpertAfterClient() throws TigaseStringprepException, InterruptedException {
    Reception.instance().clear();
    final StringBuffer track = new StringBuffer();
    final StatusTracker tracker = new StatusTracker(track);
    final ObedientExpert expert = new ObedientExpert();
    expert.addListener(tracker.expertListener());
    final ObedientClient client = new ObedientClient();
    client.addListener(tracker.clientListener());
    Reception.instance().addListener(tracker.roomListener());

    client.presence(true);
    final Room room = Reception.instance().room(client, BareJID.bareJIDInstance("room@muc.localhost"));
    room.open();
    client.activate(room);
    client.formulating();
    client.query();

    ExpertManager.instance().register(expert);
    expert.online(true);

    Thread.sleep(200);

    Assert.assertEquals(
        "Client client@localhost -> ONLINE\n" +
        "Room room@muc.localhost -> INIT\n" +
        "Room room@muc.localhost -> CLEAN\n" +
        "Client client@localhost -> FORMULATING\n" +
        "Client client@localhost -> COMMITED\n" +
        "Room room@muc.localhost -> DEPLOYED\n" +
        "Expert expert@localhost -> READY\n" +
        "Expert expert@localhost -> CHECK\n" +
        "Expert expert@localhost -> STEADY\n" +
        "Expert expert@localhost -> INVITE\n" +
        "Expert expert@localhost -> GO\n" +
        "Room room@muc.localhost -> LOCKED\n" +
        "Expert expert@localhost -> READY\n" +
        "Room room@muc.localhost -> COMPLETE\n" +
        "Client client@localhost -> FEEDBACK\n" +
        "Client client@localhost -> ONLINE\n", track.toString());
  }

  @Test
  public void testChatSuccess() throws TigaseStringprepException, InterruptedException {
    Reception.instance().clear();
    final StringBuffer track = new StringBuffer();
    final StatusTracker tracker = new StatusTracker(track);
    final ObedientExpert expert = new ObedientExpert();
    expert.addListener(tracker.expertListener());
    final ObedientClient client = new ObedientClient(1);
    client.addListener(tracker.clientListener());
    Reception.instance().addListener(tracker.roomListener());

    ExpertManager.instance().register(expert);
    expert.online(true);

    client.presence(true);
    final Room room = Reception.instance().room(client, BareJID.bareJIDInstance("room@muc.localhost"));
    client.activate(room);
    room.open();
    client.formulating();
    client.query();
    Thread.sleep(200);

    Assert.assertEquals(
            "Expert expert@localhost -> READY\n" +
            "Client client@localhost -> ONLINE\n" +
            "Room room@muc.localhost -> INIT\n" +
            "Room room@muc.localhost -> CLEAN\n" +
            "Client client@localhost -> FORMULATING\n" +
            "Client client@localhost -> COMMITED\n" +
            "Room room@muc.localhost -> DEPLOYED\n" +
            "Expert expert@localhost -> CHECK\n" +
            "Expert expert@localhost -> STEADY\n" +
            "Expert expert@localhost -> INVITE\n" +
            "Expert expert@localhost -> GO\n" +
            "Room room@muc.localhost -> LOCKED\n" +
            "Expert expert@localhost -> READY\n" +
            "Room room@muc.localhost -> COMPLETE\n" +
            "Client client@localhost -> FEEDBACK\n" +
            "Client client@localhost -> CHAT\n" +
            "Room room@muc.localhost -> DEPLOYED\n" +
            "Expert expert@localhost -> CHECK\n" +
            "Expert expert@localhost -> STEADY\n" +
            "Expert expert@localhost -> INVITE\n" +
            "Expert expert@localhost -> GO\n" +
            "Room room@muc.localhost -> LOCKED\n" +
            "Expert expert@localhost -> READY\n" +
            "Room room@muc.localhost -> COMPLETE\n" +
            "Client client@localhost -> FEEDBACK\n" +
            "Client client@localhost -> ONLINE\n", track.toString());
  }

  @Test
  public void testChat2Success() throws TigaseStringprepException, InterruptedException {
    Reception.instance().clear();
    final StringBuffer track = new StringBuffer();
    final StatusTracker tracker = new StatusTracker(track);
    final ObedientExpert expert = new ObedientExpert();
    expert.addListener(tracker.expertListener());
    final ObedientClient client = new ObedientClient(2);
    client.addListener(tracker.clientListener());
    Reception.instance().addListener(tracker.roomListener());

    ExpertManager.instance().register(expert);
    expert.online(true);

    client.presence(true);
    final Room room = Reception.instance().room(client, BareJID.bareJIDInstance("room@muc.localhost"));
    client.activate(room);
    room.open();
    client.formulating();
    client.query();
    Thread.sleep(200);

    Assert.assertEquals(
            "Expert expert@localhost -> READY\n" +
            "Client client@localhost -> ONLINE\n" +
            "Room room@muc.localhost -> INIT\n" +
            "Room room@muc.localhost -> CLEAN\n" +
            "Client client@localhost -> FORMULATING\n" +
            "Client client@localhost -> COMMITED\n" +
            "Room room@muc.localhost -> DEPLOYED\n" +
            "Expert expert@localhost -> CHECK\n" +
            "Expert expert@localhost -> STEADY\n" +
            "Expert expert@localhost -> INVITE\n" +
            "Expert expert@localhost -> GO\n" +
            "Room room@muc.localhost -> LOCKED\n" +
            "Expert expert@localhost -> READY\n" +
            "Room room@muc.localhost -> COMPLETE\n" +
            "Client client@localhost -> FEEDBACK\n" +
            "Client client@localhost -> CHAT\n" +
            "Room room@muc.localhost -> DEPLOYED\n" +
            "Expert expert@localhost -> CHECK\n" +
            "Expert expert@localhost -> STEADY\n" +
            "Expert expert@localhost -> INVITE\n" +
            "Expert expert@localhost -> GO\n" +
            "Room room@muc.localhost -> LOCKED\n" +
            "Expert expert@localhost -> READY\n" +
            "Room room@muc.localhost -> COMPLETE\n" +
            "Client client@localhost -> FEEDBACK\n" +
            "Client client@localhost -> CHAT\n" +
            "Room room@muc.localhost -> DEPLOYED\n" +
            "Expert expert@localhost -> CHECK\n" +
            "Expert expert@localhost -> STEADY\n" +
            "Expert expert@localhost -> INVITE\n" +
            "Expert expert@localhost -> GO\n" +
            "Room room@muc.localhost -> LOCKED\n" +
            "Expert expert@localhost -> READY\n" +
            "Room room@muc.localhost -> COMPLETE\n" +
            "Client client@localhost -> FEEDBACK\n" +
            "Client client@localhost -> ONLINE\n", track.toString());
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
        buffer.append("Room " + room.id() + " -> " + room.state().toString() + "\n");
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
}
