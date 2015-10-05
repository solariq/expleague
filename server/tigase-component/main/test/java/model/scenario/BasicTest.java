package model.scenario;

import com.spbsu.commons.func.Action;
import com.tbts.experts.ExpertManager;
import com.tbts.model.*;
import model.scenario.fake.ObedientClient;
import model.scenario.fake.ObedientExpert;
import org.junit.Assert;
import org.junit.Test;
import tigase.util.TigaseStringprepException;

import java.util.ArrayList;
import java.util.List;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:39
 */
public class BasicTest {

  @Test
  public void testShortSuccess() throws TigaseStringprepException {
    final StringBuffer track = new StringBuffer();
    final StatusTracker tracker = new StatusTracker(track);
    final ObedientExpert expert = new ObedientExpert();
    expert.addListener(tracker.new ExpertListener(expert));
    final ObedientClient client = new ObedientClient();
    client.addListener(tracker.new ClientListener(client));
    Reception.instance().addListener(tracker.new KeeperListener());

    ExpertManager.instance().register(expert);
    expert.online(true);

    client.presence(true);
    client.dialogue();
    client.query();

    Assert.assertEquals("Expert expert@localhost AWAY -> READY\n" +
            "Client client@localhost OFFLINE -> ONLINE\n" +
            "Client client@localhost ONLINE -> FORMULATING\n" +
            "Client client@localhost FORMULATING -> COMMITED\n" +
            "Room 0 CLEAN -> DEPLOYED\n" +
            "Expert expert@localhost READY -> STEADY\n" +
            "Room 0 DEPLOYED -> LOCKED\n" +
            "Expert expert@localhost STEADY -> GO\n" +
            "Room 0 LOCKED -> COMPLETE\n" +
            "Client client@localhost COMMITED -> FEEDBACK\n" +
            "Client client@localhost FEEDBACK -> ONLINE\n" +
            "Expert expert@localhost GO -> READY\n", track.toString());
  }

  @Test
  public void testChatSuccess() throws TigaseStringprepException {
    final StringBuffer track = new StringBuffer();
    final StatusTracker tracker = new StatusTracker(track);
    final ObedientExpert expert = new ObedientExpert();
    expert.addListener(tracker.new ExpertListener(expert));
    final ObedientClient client = new ObedientClient(1);
    client.addListener(tracker.new ClientListener(client));
    Reception.instance().addListener(tracker.new KeeperListener());

    ExpertManager.instance().register(expert);
    expert.online(true);

    client.presence(true);
    client.dialogue();
    client.query();

    Assert.assertEquals("Expert expert@localhost AWAY -> READY\n" +
            "Client client@localhost OFFLINE -> ONLINE\n" +
            "Client client@localhost ONLINE -> FORMULATING\n" +
            "Client client@localhost FORMULATING -> COMMITED\n" +
            "Room 0 CLEAN -> DEPLOYED\n" +
            "Expert expert@localhost READY -> STEADY\n" +
            "Room 0 DEPLOYED -> LOCKED\n" +
            "Expert expert@localhost STEADY -> GO\n" +
            "Expert expert@localhost GO -> READY\n" +
            "Room 0 LOCKED -> COMPLETE\n" +
            "Client client@localhost COMMITED -> FEEDBACK\n" +
            "Client client@localhost FEEDBACK -> CHAT\n" +
            "Room 0 COMPLETE -> DEPLOYED\n" +
            "Expert expert@localhost READY -> STEADY\n" +
            "Room 0 DEPLOYED -> LOCKED\n" +
            "Expert expert@localhost STEADY -> GO\n" +
            "Expert expert@localhost GO -> READY\n" +
            "Room 0 LOCKED -> COMPLETE\n" +
            "Client client@localhost CHAT -> FEEDBACK\n" +
            "Client client@localhost FEEDBACK -> ONLINE\n", track.toString());
  }

  @Test
  public void testChat2Success() throws TigaseStringprepException {
    final StringBuffer track = new StringBuffer();
    final StatusTracker tracker = new StatusTracker(track);
    final ObedientExpert expert = new ObedientExpert();
    expert.addListener(tracker.new ExpertListener(expert));
    final ObedientClient client = new ObedientClient(2);
    client.addListener(tracker.new ClientListener(client));
    Reception.instance().addListener(tracker.new KeeperListener());

    ExpertManager.instance().register(expert);
    expert.online(true);

    client.presence(true);
    client.dialogue();
    client.query();

    Assert.assertEquals("Expert expert@localhost AWAY -> READY\n" +
            "Client client@localhost OFFLINE -> ONLINE\n" +
            "Client client@localhost ONLINE -> FORMULATING\n" +
            "Client client@localhost FORMULATING -> COMMITED\n" +
            "Room 0 CLEAN -> DEPLOYED\n" +
            "Expert expert@localhost READY -> STEADY\n" +
            "Room 0 DEPLOYED -> LOCKED\n" +
            "Expert expert@localhost STEADY -> GO\n" +
            "Expert expert@localhost GO -> READY\n" +
            "Room 0 LOCKED -> COMPLETE\n" +
            "Client client@localhost COMMITED -> FEEDBACK\n" +
            "Client client@localhost FEEDBACK -> CHAT\n" +
            "Room 0 COMPLETE -> DEPLOYED\n" +
            "Expert expert@localhost READY -> STEADY\n" +
            "Room 0 DEPLOYED -> LOCKED\n" +
            "Expert expert@localhost STEADY -> GO\n" +
            "Expert expert@localhost GO -> READY\n" +
            "Room 0 LOCKED -> COMPLETE\n" +
            "Client client@localhost CHAT -> FEEDBACK\n" +
            "Client client@localhost FEEDBACK -> CHAT\n" +
            "Room 0 COMPLETE -> DEPLOYED\n" +
            "Expert expert@localhost READY -> STEADY\n" +
            "Room 0 DEPLOYED -> LOCKED\n" +
            "Expert expert@localhost STEADY -> GO\n" +
            "Expert expert@localhost GO -> READY\n" +
            "Room 0 LOCKED -> COMPLETE\n" +
            "Client client@localhost CHAT -> FEEDBACK\n" +
            "Client client@localhost FEEDBACK -> ONLINE\n", track.toString());
  }

  @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
  public static class StatusTracker {
    private final StringBuffer buffer;
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final List<Action<?>> listeners = new ArrayList<>();

    public StatusTracker(StringBuffer builder) {
      this.buffer = builder;
    }

    private class RoomListener implements Action<Room.State> {
      private final Room room;
      private Room.State current;

      public RoomListener(Room room) {
        this.room = room;
        current = room.state();
        listeners.add(this);
      }

      @Override
      public void invoke(Room.State state) {
        buffer.append("Room " + room.id() + " " + current + " -> " + state.toString() + "\n");
        current = state;
      }
    }

    public class ClientListener implements Action<Client.State> {
      private final Client client;
      private Client.State current;

      public ClientListener(Client client) {
        this.client = client;
        current = client.state();
        listeners.add(this);
      }

      @Override
      public void invoke(Client.State state) {
        buffer.append("Client " + client.id() + " " + current + " -> " + state.toString() + "\n");
        current = state;
      }
    }

    public class ExpertListener implements Action<Expert.State> {
      private final Expert expert;
      private Expert.State current;

      public ExpertListener(Expert expert) {
        this.expert = expert;
        current = expert.state();
        listeners.add(this);
      }

      @Override
      public void invoke(Expert.State state) {
        buffer.append("Expert " + expert.id() + " " + current + " -> " + state.toString() + "\n");
        current = state;
      }
    }

    public class KeeperListener implements Action<Room> {
      public KeeperListener() {
        listeners.add(this);
      }

      @Override
      public void invoke(Room room) {
        room.addListener(new RoomListener(room));
      }
    }
  }
}
