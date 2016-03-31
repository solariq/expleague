package com.expleague.server.admin.dto;

import com.expleague.xmpp.stanza.Stanza;
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

  public DumpItemDto(final Stanza stanza) {
    this.stanza = stanza.xmlString();
    this.author = stanza.from().toString();
    this.timestamp = stanza.getTimestampMs();
  }
}
