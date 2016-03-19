package com.expleague.server.admin.dto;

import com.expleague.model.Filter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author vpdelta
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FilterDto {
  @JsonProperty
  private List<JIDDto> reject;

  @JsonProperty
  private List<JIDDto> accept;

  @JsonProperty
  private List<JIDDto> prefer;

  public FilterDto(final Filter filter) {
    this.reject = filter.rejected().map(JIDDto::new).collect(Collectors.toList());
    this.accept = filter.accepted().map(JIDDto::new).collect(Collectors.toList());
    this.prefer = filter.preferred().map(JIDDto::new).collect(Collectors.toList());
  }
}
