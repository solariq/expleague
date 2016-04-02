package com.expleague.server.admin.series;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author vpdelta
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TimeSeriesChartDto {
  @JsonProperty
  private final String chartTitle;

  @JsonProperty
  private final String xAxisTitle;

  @JsonProperty
  private final String yAxisTitle;

  @JsonProperty
  private final List<TimeSeriesDto> timeSeries;

  public TimeSeriesChartDto(final String chartTitle, final String xAxisTitle, final String yAxisTitle, final List<TimeSeriesDto> timeSeries) {
    this.chartTitle = chartTitle;
    this.xAxisTitle = xAxisTitle;
    this.yAxisTitle = yAxisTitle;
    this.timeSeries = timeSeries;
  }
}
