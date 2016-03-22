package com.expleague.server.dao;

import com.expleague.server.ExpLeagueServer;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Stanza;

import java.util.stream.Stream;

/**
 * User: solar
 * Date: 28.10.15
 * Time: 18:09
 */
public interface Archive {
  static Archive instance() {
    return ExpLeagueServer.archive();
  }

  Dump dump(String local);
  Dump register(String room, String owner);

  interface DumpItem {
    Stanza stanza();
    String author();
    long timestamp();
  }

  interface Dump {
    void accept(Stanza stanza);
    Stream<DumpItem> stream();
    JID owner();
  }
}
