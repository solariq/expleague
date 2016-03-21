package com.expleague.server.admin.dto;

import com.expleague.model.Image;
import com.expleague.model.Offer;
import com.expleague.xmpp.Item;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author vpdelta
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OfferDto {
  @JsonProperty
  private JIDDto room;

  @JsonProperty
  private JIDDto client;

  @JsonProperty
  private String topic;

  @JsonProperty
  private FilterDto filter;

  @JsonProperty
  private List<ImageDto> images;

  @JsonProperty
  private Boolean isLocal;

  @JsonProperty
  private Offer.Urgency urgency;

  @JsonProperty
  private LocationDto location;

  @JsonProperty
  private Double started;

  @JsonProperty
  private long expiresMs;

  @JsonProperty
  private List<ExpertsProfileDto> workers;

  public OfferDto(final Offer offer) {
    this.room = new JIDDto(offer.room());
    this.client = new JIDDto(offer.client());
    this.topic = offer.topic();
    this.filter = new FilterDto(offer.filter());
    this.images = new ArrayList<>();
    for (Item item : offer.attachments()) {
      if (item instanceof Image) {
        images.add(new ImageDto((Image) item));
      }
    }
    this.isLocal = offer.geoSpecific();
    this.urgency = offer.urgency();

    final Offer.Location location = offer.location();
    if (location != null) {
      this.location = new LocationDto(location);
    }

    this.started = offer.started();
    this.expiresMs = (long) (this.started * 1000 + this.urgency.time());
    this.workers = offer.workers()
      .filter(expertsProfile -> expertsProfile.jid() != null)
      .map(ExpertsProfileDto::new)
      .collect(Collectors.toList());
  }
}
