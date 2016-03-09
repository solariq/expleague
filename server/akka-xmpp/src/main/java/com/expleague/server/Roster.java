package com.expleague.server;

import com.expleague.xmpp.control.register.Query;

/**
 * User: solar
 * Date: 11.12.15
 * Time: 18:37
 */
public interface Roster {
  XMPPDevice register(Query query) throws Exception;
  Query required();

  XMPPDevice device(String name);
  XMPPUser user(String name);
  XMPPDevice[] devices(String id);

  static Roster instance() {
    return ExpLeagueServer.roster();
  }
}
