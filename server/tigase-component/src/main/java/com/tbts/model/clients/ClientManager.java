package com.tbts.model.clients;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.com.tbts.db.DAO;
import com.tbts.model.Client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

  private Map<String, Client> clients = new HashMap<>();

  public synchronized Client byJID(String jid) {
    Client client = clients.get(jid);
    if (client == null) {
      if (jid.startsWith("muc.") || jid.contains("@muc.") || !jid.contains("@"))
        return null;
      final Client newClient = DAO.instance().createClient(jid);
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

  public synchronized List<String> online() {
    final List<String> result = new ArrayList<>(clients.size());
    for (final String jid : clients.keySet()) {
      if (clients.get(jid).state() != Client.State.ONLINE)
        result.add(jid);
    }
    return result;
  }
}
