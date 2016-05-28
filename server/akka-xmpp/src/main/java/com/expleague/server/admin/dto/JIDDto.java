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

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final JIDDto jidDto = (JIDDto) o;

    if (bare != null ? !bare.equals(jidDto.bare) : jidDto.bare != null)
      return false;
    return resource != null ? resource.equals(jidDto.resource) : jidDto.resource == null;

  }

  @Override
  public int hashCode() {
    int result = bare != null ? bare.hashCode() : 0;
    result = 31 * result + (resource != null ? resource.hashCode() : 0);
    return result;
  }
}
