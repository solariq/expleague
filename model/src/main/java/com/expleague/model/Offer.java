package com.expleague.model;

import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.spbsu.commons.seq.CharSeqTools;

import javax.xml.bind.annotation.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * User: solar
 * Date: 17.12.15
 * Time: 16:38
 */

@SuppressWarnings("unused")
@XmlRootElement
public class Offer extends Item {
  private static final Logger log = Logger.getLogger(Offer.class.getName());
  @XmlAttribute
  private JID room;
  @XmlAttribute
  private JID client;

  @XmlElement(namespace = Operations.NS)
  private String topic;

  @XmlElements(value = {
    @XmlElement(name="image", namespace = Operations.NS, type = Image.class),
    @XmlElement(name="experts-filter", namespace = Operations.NS, type = Filter.class)
  })
  private List<Attachment> attachments;

  @XmlAttribute(name = "local")
  private Boolean isLocal;

  @XmlAttribute
  private Urgency urgency;

  @XmlElement(namespace = Operations.NS)
  private Location location;

  @XmlElement(namespace = Operations.NS)
  private String comment;

  @XmlElement(namespace = Operations.NS)
  private String draft;

  @XmlAttribute
  private Double started;

  @XmlElementWrapper(namespace = Operations.NS)
  @XmlAnyElement(lax = true)
  private List<ExpertsProfile> workers;

  @XmlElementWrapper(namespace = Operations.NS)
  @XmlAnyElement(lax = true)
  private List<Tag> tags;

  @XmlElementWrapper(namespace = Operations.NS)
  @XmlAnyElement(lax = true)
  private List<Pattern> patterns;

  public Offer() {
  }

  public static Offer create(JID room, JID client, Message description) {
    if (description.has(Offer.class)) {
      final Offer offer = description.get(Offer.class);
      offer.room = room;
      offer.client = client;
      return offer;
    }
    if (description.has(Message.Subject.class)) {
      final Offer result;
      final Message.Subject subject = description.get(Message.Subject.class);
      final String value = subject.value();
      if (value.startsWith("{")) { // JSON
        try {
          final JsonNode node = tlObjectMapper.get().readTree(value);
          result = new Offer();
          result.client = client;
          result.room = room;
          result.topic = node.get("topic").asText();
          result.urgency = node.has("urgency") ? Urgency.valueOf(node.get("urgency").asText().toUpperCase()) : null;
          result.started = node.has("started") ? node.get("started").asDouble() : System.currentTimeMillis() / 1000.;
          result.isLocal = node.has("local") && node.get("local").asBoolean();
          result.location = node.has("location") ? new Location(
              node.get("location").get("longitude").asDouble(),
              node.get("location").get("latitude").asDouble()
          ) : null;
          if (node.has("attachments")) {
            for (final CharSequence part: CharSeqTools.split(node.get("attachments").asText(), ",")) {
              result.attachments = new ArrayList<>();
              final String name = part.toString().trim();
              if (name.endsWith(".jpeg")) {
                result.attachments.add(new Image(name, client));
              }
            }
          }
        }
        catch (IOException e) {
          throw new IllegalArgumentException("Unable to parse JSON offer as tree!", e);
        }
      }
      else {
        result = new Offer();
        result.client = client;
        result.room = room;
        result.topic = value;
      }
      return result;
    }
    throw new IllegalArgumentException("Unable to restore offer from: " + description);
  }

  public JID room() {
    return room;
  }

  public String topic() {
    return topic;
  }

  public Item[] attachments() {
    try {
      return attachments != null ? attachments.toArray(new Item[attachments.size()]) : new Item[0];
    } catch (Exception e) {
      return new Item[0];
    }
  }

  public Date expires() {
    return new Date((long) (started() * 1000 + urgency().time()));
  }

  public double started() {
    return started = (started != null ? started : System.currentTimeMillis() / 1000.);
  }

  public Urgency urgency() {
    return urgency != null ? urgency : Urgency.ASAP;
  }

  public boolean geoSpecific() {
    return isLocal != null ? isLocal : false;
  }

  public Location location() {
    return location;
  }

  public JID client() {
    return client;
  }

  public boolean fit(JID expert) {
    if (attachments == null)
      return true;
    final Optional<Filter> filter = attachments.stream().filter(a -> a instanceof Filter).map(a -> (Filter) a).findFirst();
    return !filter.isPresent() || filter.get().fit(expert);
  }

  public void topic(String topic) {
    this.topic = topic;
  }

  public Filter filter() {
    if (attachments == null)
      attachments = new ArrayList<>();
    final Optional<Filter> filterOpt = attachments.stream().filter(a -> a instanceof Filter).map(a -> (Filter) a).findFirst();
    if (filterOpt.isPresent())
      return filterOpt.get();
    final Filter result = new Filter();
    attachments.add(result);
    return result;
  }

  private void recoverFilter() {
  }

  public void addWorker(ExpertsProfile profile) {
    if (workers == null)
      workers = new ArrayList<>();
    workers.add(profile);
  }

  public Stream<ExpertsProfile> workers() {
    return workers != null ? workers.stream() : Stream.empty();
  }

  public void client(JID owner) {
    this.client = owner;
  }

  public void room(JID room) {
    this.room = room;
  }

  public void attach(Image image) {
    if (attachments == null)
      attachments = new ArrayList<>();
    attachments.add(image);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Offer offer = (Offer) o;
    return room.equals(offer.room) && started.equals(offer.started);
  }

  @Override
  public int hashCode() {
    int result = room.hashCode();
    result = 31 * result + started.hashCode();
    return result;
  }

  @XmlEnum
  public enum Urgency {
    @XmlEnumValue("asap") ASAP(TimeUnit.HOURS.toMillis(1)),
    @XmlEnumValue("day") DAY(TimeUnit.DAYS.toMillis(1)),
    @XmlEnumValue("week") WEEK(TimeUnit.DAYS.toMillis(7)),;

    private final long time;

    Urgency(long time) {
      this.time = time;
    }

    public long time() {
      return time;
    }
  }

  @XmlRootElement
  public static class Location extends Item {
    @XmlAttribute
    private double longitude;

    @XmlAttribute
    private double latitude;

    public Location() {}
    public Location(double longitude, double latitude) {
      this.longitude = longitude;
      this.latitude = latitude;
    }

    public double longitude() {
      return longitude;
    }

    public double latitude() {
      return latitude;
    }
  }
}
