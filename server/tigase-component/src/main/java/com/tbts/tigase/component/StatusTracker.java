package com.tbts.tigase.component;

import com.spbsu.commons.func.Action;
import com.tbts.model.Client;
import com.tbts.model.Expert;
import com.tbts.model.Room;
import com.tbts.model.handlers.ClientManager;
import com.tbts.model.handlers.ExpertManager;
import com.tbts.model.handlers.Reception;

import java.io.PrintStream;

/**
 * User: solar
 * Date: 06.10.15
 * Time: 14:04
 */
@SuppressWarnings("StringConcatenationInsideStringBufferAppend")
public class StatusTracker {
  private final PrintStream stream;
  private Action<Client> clientListener  = new Action<Client>() {
    public void invoke(Client client) {
      Client.State state = client.state();
      if (state == null)
        throw new NullPointerException();
      stream.append("Client " + client.id() + " -> " + state.toString() + "\n");
    }
  };
  private Action<Expert> expertListener  = new Action<Expert>() {
    public void invoke(Expert expert) {
      stream.append("Expert " + expert.id() + " -> " + expert.state().toString() + "\n");
    }
  };

  private Action<Room> roomListener  = new Action<Room>() {
    public void invoke(Room room) {
      stream.append("Room " + room.id() + " -> " + room.state().toString() + "\n");
    }
  };

  public StatusTracker(PrintStream builder) {
    this.stream = builder;
    ExpertManager.instance().addListener(expertListener());
    ClientManager.instance().addListener(clientListener());
    Reception.instance().addListener(roomListener());
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

