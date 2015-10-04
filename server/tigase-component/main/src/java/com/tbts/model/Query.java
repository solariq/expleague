package com.tbts.model;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 20:16
 */
public class Query {
  private final String text;

  public Query(String text) {
    this.text = text;
  }

  public String text() {
    return text;
  }
}
