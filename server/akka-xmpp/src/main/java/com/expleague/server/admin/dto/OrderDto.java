package com.expleague.server.admin.dto;

import com.expleague.model.OrderState;
import com.expleague.server.agents.ExpLeagueOrder;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author vpdelta
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderDto {
  @JsonProperty
  private final OfferDto offer;

  @JsonProperty
  private final OrderState status;

  @JsonProperty
  private final List<ParticipantDto> participants;

  @JsonProperty
  private final double feedback;

  @JsonProperty
  private final List<StatusHistoryRecordDto> statusHistoryRecords;

  public OrderDto(final ExpLeagueOrder order) {
    this.offer = new OfferDto(order.offer());
    this.status = order.state();
    this.participants = order.participants().map(jid -> new ParticipantDto(
      new JIDDto(jid),
      order.role(jid)
    )).collect(Collectors.toList());
    this.feedback = order.feedback();
    this.statusHistoryRecords = order.statusHistoryRecords()
      .map(StatusHistoryRecordDto::new)
      .collect(Collectors.toList());
  }

  public OfferDto getOffer() {
    return offer;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ParticipantDto {
    @JsonProperty
    private JIDDto jid;

    @JsonProperty
    private ExpLeagueOrder.Role role;

    public ParticipantDto(final JIDDto jid, final ExpLeagueOrder.Role role) {
      this.jid = jid;
      this.role = role;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class StatusHistoryRecordDto {
    @JsonProperty
    private OrderState status;

    @JsonProperty
    private long timestamp;

    public StatusHistoryRecordDto(final ExpLeagueOrder.StatusHistoryRecord statusHistoryRecord) {
      this.status = statusHistoryRecord.getStatus();
      this.timestamp = statusHistoryRecord.getDate().getTime();
    }
  }
}
