package com.expleague.server.dao.fake;

import com.expleague.server.dao.Archive;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Stanza;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * User: solar
 * Date: 16.11.15
 * Time: 22:36
 */
@SuppressWarnings("unused")
public class InMemArchive implements Archive {
  final Map<String, MyDump> map = new HashMap<>();

  @Override
  public Dump dump(String local) {
    return map.get(local);
  }

  @Override
  public Dump register(String room, String owner) {
    final MyDump dump = new MyDump(owner);
    map.put(room, dump);
    return dump;
  }

  private class MyDump implements Dump {
    private final List<Stanza> snapshot = new ArrayList<>();
    private final String owner;

    private MyDump(String owner) {
      this.owner = owner;
    }

    @Override
    public void accept(Stanza stanza) {
      snapshot.add(stanza);
    }

    @Override
    public Stream<Stanza> stream() {
      return snapshot.stream();
    }

    @Override
    public JID owner() {
      return JID.parse(owner);
    }
  }
}
