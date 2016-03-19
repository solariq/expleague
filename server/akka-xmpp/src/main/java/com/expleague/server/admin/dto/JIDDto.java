package com.expleague.server.admin.dto;

import com.expleague.xmpp.JID;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author vpdelta
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JIDDto {
  @JsonProperty
  private String bare;

  @JsonProperty
  private String resource;

  public JIDDto(final JID jid) {
    this.bare = jid.bare().getAddr();
    this.resource = jid.resource();
  }
}
