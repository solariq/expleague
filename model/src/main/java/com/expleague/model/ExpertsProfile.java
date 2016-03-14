package com.expleague.model;

import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;

import javax.xml.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 27/01/16.
 */

@SuppressWarnings("unused")
@XmlRootElement(name = "expert")
public class ExpertsProfile extends Item {
  @XmlAttribute
  private JID jid;

  @XmlAttribute
  private String login;


  @XmlAttribute
  private String name;

  @XmlAttribute
  private Integer tasks;

  @XmlAttribute
  private Education education = Education.MEDIUM;

  @XmlAttribute
  private Boolean available;

  @XmlElementWrapper(name = "tags", namespace = Operations.NS)
  @XmlElements({@XmlElement(name = "tag", namespace = Operations.NS, type = Tag.class)})
  List<Tag> tags;

  @XmlAttribute
  double rating = 0;

  @XmlAttribute
  int basedOn = 0;

  @XmlElement(namespace = Operations.NS)
  private String avatar;

  public ExpertsProfile() {}

  public JID jid() {
    return jid;
  }

  private ExpertsProfile(JID jid) {
    this.jid = jid;
    login = jid.local();
  }

  public String avatar() {
    return avatar;
  }

  public String name() {
    return name;
  }

  public void available(boolean value) {
    this.available = value;
  }

  public Stream<Tag> tags() {
    return tags.stream();
  }

  @SuppressWarnings("unused")
  public static class Builder {
    private final ExpertsProfile result;
    private final Map<String, Stat> tags = new HashMap<>();
    public Builder(JID id) {
      result = new ExpertsProfile(id);
    }

    public Builder name(String name) {
      result.name = name;
      return this;
    }

    public Builder tasks(int tasks) {
      result.tasks = tasks;
      return this;
    }

    public Builder score(double score) {
      if (score > 0) {
        result.rating += score;
        result.basedOn++;
      }
      return this;
    }

    public Builder tag(String name, double score) {
      final Stat stat = tags.getOrDefault(name, new Stat());
      stat.weight ++;
      stat.scoreSum += score;
      tags.put(name, stat);
      return this;
    }

    public Builder avatar(String ava) {
      result.avatar = ava;
      return this;
    }

    public Builder available(boolean online) {
      result.available = online;
      return this;
    }

    public Builder education(Education degree) {
      result.education = degree;
      return this;
    }

    public ExpertsProfile build() {
      result.tags = tags.isEmpty() ? null : tags.entrySet().stream().map(
          entry -> new Tag(entry.getKey(), entry.getValue().scoreSum / (50 + entry.getValue().scoreSum))
      ).collect(Collectors.toList());
      if (result.basedOn > 0) {
        result.rating /= result.basedOn;
      }
      return result;
    }

    private static class Stat {
      double scoreSum;
      double weight;
    }
  }

  @XmlEnum
  public enum Education {
    @XmlEnumValue("doctor") WELL_DONE,
    @XmlEnumValue("phd") MEDIUM_WELL,
    @XmlEnumValue("high") MEDIUM,
    @XmlEnumValue("undergrad") MEDIUM_RARE,
    @XmlEnumValue("school") RARE,
    @XmlEnumValue("preschool") BLOODY,
  }
}
