package com.expleague.server.admin.dto;

import com.expleague.model.Image;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author vpdelta
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImageDto {
  @JsonProperty
  private String src;

  public ImageDto(final Image image) {
    this.src = image.url();
  }
}
