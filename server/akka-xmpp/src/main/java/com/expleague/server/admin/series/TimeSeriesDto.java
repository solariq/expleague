package com.expleague.server.admin.series;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author vpdelta
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TimeSeriesDto {
  @JsonProperty
  private final String title;

  @JsonProperty
  private final List<PointDto> points;

  public TimeSeriesDto(final String title, final List<PointDto> points) {
    this.title = title;
    this.points = points;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PointDto {
    @JsonProperty
    private final long timestamp;

    @JsonProperty
    private final double value;

    // todo: point attributes?

    public PointDto(final long timestamp, final double value) {
      this.timestamp = timestamp;
      this.value = value;
    }
  }
}
