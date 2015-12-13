package com.tbts.model.handlers;

import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.tbts.model.Client;

/**
 * User: solar
 * Date: 05.10.15
 * Time: 19:56
 */
public class ClientManager extends WeakListenerHolderImpl<Client> implements Action<Client> {
  public static final ClientManager CLIENT_MANAGER = new ClientManager();

  public static ClientManager instance() {
    return CLIENT_MANAGER;
  }

  public Client get(String jid) {
    Client client = DAO.instance().client(jid);
    if (client == null) {
      if (jid.contains("@muc.") || !jid.contains("@") || jid.startsWith("experts-admin@"))
        return null;
      client = DAO.instance().createClient(jid);
      invoke(client);
    }
    return client;
  }

  public void invoke(Client c) {
    super.invoke(c);
  }
}
