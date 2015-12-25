package com.tbts.server.roster;

import com.tbts.server.JabberUser;
import com.tbts.server.Roster;
import com.tbts.xmpp.control.register.Query;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 25.12.15
 * Time: 7:38
 */
public class InMemRoster implements Roster {
  private static final Logger log = Logger.getLogger(InMemRoster.class.getName());
  final Map<String, String> passwds = new HashMap<>();
  @Override
  public Query required() {
    final Query query = new Query();
    query.name("");
    query.passwd("");
    return query;
  }

  @Override
  public void register(Query query) throws Exception {
    log.log(Level.FINE, "Registering user " + query.name());
    passwds.put(query.name(), query.passwd());
  }

  @Override
  public synchronized JabberUser byName(String name) {
    if (passwds.containsKey(name))
      return new JabberUser(name, passwds.get(name));
    else
      return null;
  }
}
