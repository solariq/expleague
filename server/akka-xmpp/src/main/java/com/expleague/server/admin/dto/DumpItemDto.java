package com.expleague.server.admin.dto;

import com.expleague.server.dao.Archive;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author vpdelta
 */
public class DumpItemDto {
  @JsonProperty
  private final String stanza;

  @JsonProperty
  private final String author;

  @JsonProperty
  private final long timestamp;

  public DumpItemDto(final Archive.DumpItem dumpItem) {
    this.stanza = dumpItem.stanza().xmlString();
    this.author = dumpItem.author();
    this.timestamp = dumpItem.timestamp();
  }
}
