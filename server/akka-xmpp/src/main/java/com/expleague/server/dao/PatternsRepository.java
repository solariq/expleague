package com.expleague.server.dao;

import com.expleague.model.Pattern;
import com.expleague.server.ExpLeagueServer;
import com.expleague.util.stream.RequiresClose;

import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 28/03/16.
 */
public interface PatternsRepository {
  static PatternsRepository instance() {
    return ExpLeagueServer.patterns();
  }

  Stream<Pattern> all();
}
