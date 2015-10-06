package com.tbts.model.clients;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.model.Client;
import com.tbts.model.impl.ClientImpl;
import tigase.xmpp.BareJID;

import java.util.HashMap;
import java.util.Map;

/**
 * User: solar
 * Date: 05.10.15
 * Time: 19:56
 */
public class ClientManager extends WeakListenerHolderImpl<Client> implements Action<Client> {
  public static final ClientManager CLIENT_MANAGER = new ClientManager();

  public synchronized static ClientManager instance() {
    return CLIENT_MANAGER;
  }

  private Map<BareJID, Client> clients = new HashMap<>();

  public Client byJID(BareJID jid) {
    Client client = clients.get(jid);
    if (client == null) {
      final Client newClient = new ClientImpl(jid);
      clients.put(jid, newClient);
      newClient.addListener(this);
      invoke(newClient);
      client = newClient;
    }
    return client;
  }

  public void invoke(Client c) {
    super.invoke(c);
  }
}
