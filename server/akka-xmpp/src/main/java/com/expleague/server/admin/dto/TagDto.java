package com.expleague.server.admin.dto;

import com.expleague.model.Tag;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author vpdelta
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TagDto {
  @JsonProperty
  private String name;

  @JsonProperty
  private double score;

  public TagDto(final Tag tag) {
    this.name = tag.name();
    this.score = tag.score();
  }
}
