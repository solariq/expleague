package com.tbts.server.roster;

import com.tbts.server.JabberUser;
import com.tbts.server.Roster;
import com.tbts.xmpp.control.register.Query;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
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
  final Map<String, Properties> properties = new HashMap<>();
  @Override
  public Query required() {
    final Query query = new Query();
    query.username("");
    query.passwd("");
    return query;
  }

  @Override
  public void register(Query query) throws Exception {
    log.log(Level.FINE, "Registering user " + query.username());
    passwds.put(query.username(), query.passwd());
    final Properties props = new Properties();
    if (query.name() != null)
      props.put("name", query.name());
    if (query.avatar() != null)
      props.put("avatar", query.avatar());
    if (query.city() != null)
      props.put("city", query.city());
    if (query.country() != null)
      props.put("country", query.country());
    properties.put(query.username(), props);
  }

  @Override
  public synchronized JabberUser byName(String name) {
    if (passwds.containsKey(name)) {
      final Properties properties = this.properties.get(name);
      return new JabberUser(
          name,
          passwds.get(name),
          properties.getProperty("country"),
          properties.getProperty("city"),
          properties.getProperty("avatar"),
          properties.getProperty("name")
      );
    }
    else
      return null;
  }
}
