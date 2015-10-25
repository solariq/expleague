package com.tbts.model.clients;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.com.tbts.db.DAO;
import com.tbts.model.Client;

import java.util.ArrayList;
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

  public Map<String, Client> clients() {
    return DAO.instance().clients();
  }
  public synchronized Client byJID(String jid) {
    Client client = clients().get(jid);
    if (client == null) {
      if (jid.contains("@muc.") || !jid.contains("@"))
        return null;
      final Client newClient = DAO.instance().createClient(jid);
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
    final Map<String, Client> clients = clients();
    final List<String> result = new ArrayList<>(clients.size());
    for (final String jid : clients.keySet()) {
      if (clients.get(jid).state() != Client.State.ONLINE)
        result.add(jid);
    }
    return result;
  }
}
