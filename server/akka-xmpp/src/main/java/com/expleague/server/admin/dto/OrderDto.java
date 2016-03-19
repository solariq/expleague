package com.expleague.server.admin.dto;

import com.expleague.server.agents.ExpLeagueOrder;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author vpdelta
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderDto {
  @JsonProperty
  private final OfferDto offer;

  @JsonProperty
  private final ExpLeagueOrder.Status status;

  public OrderDto(final ExpLeagueOrder order) {
    this.offer = new OfferDto(order.offer());
    this.status = order.status();
  }
}
