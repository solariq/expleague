package com.expleague.server.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author vpdelta
 */
public class OrdersGroupDto {
  @JsonProperty
  private final String groupName;

  @JsonProperty
  private final List<OrderDto> orders;

  public OrdersGroupDto(final String groupName, final List<OrderDto> orders) {
    this.groupName = groupName;
    this.orders = orders;
  }

  public String getGroupName() {
    return groupName;
  }

  public List<OrderDto> getOrders() {
    return orders;
  }
}
