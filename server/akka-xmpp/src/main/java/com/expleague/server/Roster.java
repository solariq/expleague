package com.expleague.server;

import com.expleague.xmpp.control.register.Query;

/**
 * User: solar
 * Date: 11.12.15
 * Time: 18:37
 */
public interface Roster {
  void register(Query query) throws Exception;
  Query required();
  JabberUser byName(String name);

  static Roster instance() {
    return ExpLeagueServer.roster();
  }
}
