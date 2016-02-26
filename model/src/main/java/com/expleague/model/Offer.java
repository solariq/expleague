package com.expleague.model;

import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.spbsu.commons.seq.CharSeqTools;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

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

  @XmlAnyElement(lax = true)
  private List<Item> attachments;

  @XmlAttribute(name = "local")
  private Boolean isLocal;

  @XmlAttribute(name = "specific")
  private Boolean isSpecific;

  @XmlAttribute
  private Urgency urgency;

  @XmlElement(namespace = Operations.NS)
  private Location location;

  @XmlAttribute
  private Double started;

  @XmlElementWrapper(namespace = Operations.NS)
  @XmlAnyElement(lax = true)
  private Set<ExpertsProfile> workers;

  @XmlElement(namespace = Operations.NS)
  private Set<JID> slackers;

  public Offer() {
  }

  public static Offer create(JID room, JID client, Message description) {
    if (description.has(Offer.class))
      return description.get(Offer.class);
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
          result.isSpecific = node.has("specific") && node.get("specific").asBoolean();
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

  @Nullable
  public ExpertsProfile worker(JID jid) {
    if (workers == null)
      return null;
    final Optional<ExpertsProfile> any = workers.stream().filter(p -> p.login().equals(jid.local()) || p.login().equals(jid.resource())).findAny();
    return any.isPresent() ? any.get() : null;
  }

  public JID room() {
    return room;
  }

  public String topic() {
    return topic;
  }

  public Item[] attachments() {
    return attachments != null ? attachments.toArray(new Item[attachments.size()]) : new Item[0];
  }

  public void addWorker(ExpertsProfile worker) {
    if (workers == null)
      workers = new HashSet<>();
    workers.add(worker);
  }

  public void addSlacker(JID worker) {
    if (slackers == null)
      slackers = new HashSet<>();
    slackers.add(worker);
  }

  public boolean hasWorker(JID worker) {
    return workers != null && workers.stream().anyMatch(profile -> profile.login().equals(worker.local())) && (slackers == null || !slackers.contains(worker));
  }

  public boolean hasSlacker(JID worker) {
    return slackers != null && slackers.contains(worker);
  }

  public Date expires() {
    return new Date((long) (started() * 1000 + urgency().time()));
  }

  private double started() {
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

  public Set<ExpertsProfile> workers() {
    return workers != null ? workers : Collections.emptySet();
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
