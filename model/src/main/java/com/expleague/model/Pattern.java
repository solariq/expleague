package com.expleague.model;

import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.*;
import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 28/03/16.
 */
@SuppressWarnings("unused")
@XmlRootElement
public class Pattern extends Item {
  @XmlAttribute
  private String name;

  @XmlAttribute
  private Type type = Type.ANSWER;

  @XmlElement(namespace = Operations.NS)
  private String body;

  @XmlElement(namespace = Operations.NS)
  private String icon;

  public Pattern(String name, String body, String icon, Type type) {
    this.name = name;
    this.body = body;
    this.icon = icon;
    this.type = type;
  }

  public Pattern() {
  }

  public Pattern(String name, String icon) {
    this.name = name;
    this.icon = icon;
  }

  @Override
  public String toString() {
    return name;
  }

  public CharSequence body() {
    return body;
  }

  public String name() {
    return name;
  }

  public Type type() {
    return type;
  }

  public Pattern presentation() {
    if (type != Type.ANSWER)
      return null;
    return new Pattern(name, icon);
  }

  @XmlEnum
  public enum Type {
    @XmlEnumValue("answer") ANSWER(0),
    @XmlEnumValue("chat") CHAT(1),
    @XmlEnumValue("hello") HELLO(2),
    ;

    int code;
    Type(int code) {
      this.code = code;
    }

    public int code() {
      return code;
    }
    public static Type valueOf(int index) {
      return Stream.of(Type.values()).filter(s -> s.code == index).findAny().orElse(null);
    }
  }
}
