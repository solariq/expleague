package com.tbts.model;

import com.spbsu.commons.util.Pair;
import com.tbts.server.agents.roles.ExpertRole;
import com.tbts.xmpp.JID;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * User: solar
 * Date: 19.12.15
 * Time: 15:12
 */
public class ExpertManager {
  private static ExpertManager instance = new ExpertManager();
  public static ExpertManager instance() {
    return instance;
  }

  private final Map<JID, Record> records = new ConcurrentHashMap<>();
  public Record record(JID jid) {
    records.putIfAbsent(jid, new Record());
    return records.get(jid);
  }

  public static class Record {
    private List<Pair<JID, ExpertRole.State>> entries = new ArrayList<>();
    public void entry(Offer offer, ExpertRole.State state) {
      entries.add(Pair.create(offer != null ? offer.room() : null, state));
    }

    public Stream<Pair<JID, ExpertRole.State>> entries() {
      return entries.stream();
    }
  }
}
