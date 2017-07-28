package com.expleague.server.dao;

import com.expleague.server.ExpLeagueServer;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Stanza;

import java.util.List;
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
  String lastMessageId(String local);

  interface Dump {
    <T extends Stanza> void accept(T stanza);
    void commit();
    Stream<Stanza> stream();
    JID owner();

    int size();
  }
}
