package com.expleague.server.dao.fake;

import com.expleague.server.dao.Archive;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Stanza;

import java.util.*;
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
    return map.compute(local, (k,v) -> v != null ? v : new MyDump(k));
  }

  @Override
  public Dump register(String room, String owner) {
    final MyDump dump = new MyDump(owner);
    map.put(room, dump);
    return dump;
  }

  private class MyDump implements Dump {
    private final Set<String> known = new HashSet<>();
    private final List<Stanza> snapshot = new ArrayList<>();
    private final String owner;

    private MyDump(String owner) {
      this.owner = owner;
    }

    @Override
    public void accept(Stanza stanza) {
      if (known.contains(stanza.id()))
        return;
      snapshot.add(stanza);
      known.add(stanza.id());
    }

    @Override
    public void commit() {}

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
