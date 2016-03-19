package com.expleague.server.admin.dto;

import com.expleague.model.ExpertsProfile;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author vpdelta
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExpertsProfileDto {
  @JsonProperty
  private JIDDto jid;

  @JsonProperty
  private String login;

  @JsonProperty
  private String name;

  @JsonProperty
  private Integer tasks;

  @JsonProperty
  private ExpertsProfile.Education education = ExpertsProfile.Education.MEDIUM;

  @JsonProperty
  private Boolean available;

  @JsonProperty
  List<TagDto> tags;

  @JsonProperty
  double rating = 0;

  @JsonProperty
  int basedOn = 0;

  @JsonProperty
  private String avatar;

  public ExpertsProfileDto(final ExpertsProfile expertsProfile) {
    this.jid = new JIDDto(expertsProfile.jid());
    this.login = expertsProfile.login();
    this.name = expertsProfile.name();
    this.tasks = expertsProfile.tasks();
    this.education = expertsProfile.education();
    this.available = Boolean.TRUE.equals(expertsProfile.isAvailable());
    this.tags = expertsProfile.tags().map(TagDto::new).collect(Collectors.toList());
    this.rating = expertsProfile.rating();
    this.basedOn = expertsProfile.basedOn();
    this.avatar = expertsProfile.avatar();
  }
}
