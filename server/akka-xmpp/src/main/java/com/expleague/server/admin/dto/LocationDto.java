package com.expleague.server.admin.dto;

import com.expleague.model.Offer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author vpdelta
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationDto {
  @JsonProperty
  private double longitude;

  @JsonProperty
  private double latitude;

  public LocationDto(final Offer.Location location) {
    this.longitude = location.longitude();
    this.latitude = location.latitude();
  }
}
