package com.tbts.server;

import org.jetbrains.annotations.Nullable;

/**
 * User: solar
 * Date: 11.12.15
 * Time: 18:39
 */
public class JabberUser {
  private final String passwd;
  private final String name;
  private final String country;
  private final String city;
  private final String avatar;
  private final String realName;

  public JabberUser(String name, String passwd, String country, String city, String avatar, String realName) {
    this.passwd = passwd;
    this.name = name;
    this.country = country;
    this.city = city;
    this.avatar = avatar;
    this.realName = realName;
  }

  public String name() {
    return name;
  }

  public String passwd() {
    return passwd;
  }

  @Nullable
  public String avatar() {
    return avatar;
  }

  @Nullable
  public String city() {
    return city;
  }

  @Nullable
  public String country() {
    return country;
  }

  @Nullable
  public String realName() {
    return realName;
  }
}
