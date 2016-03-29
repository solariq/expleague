package com.expleague.server.dao.fake;

import com.expleague.model.Pattern;
import com.expleague.server.dao.PatternsRepository;

import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 28/03/16.
 */
public class InMemPatterns implements PatternsRepository {
  @Override
  public Stream<Pattern> all() {
    return Stream.empty();
  }
}
