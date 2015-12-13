package com.tbts.model;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 20:16
 */
public class Query {
  private String text;

  public Query(String text) {
    this.text = text;
  }

  public String text() {
    return text;
  }

  public static class Builder {
    final StringBuilder textBuilder = new StringBuilder();
    public void addText(String text) {
      textBuilder.append(text);
    }

    public Query build() {
      return new Query(textBuilder.toString());
    }
  }
}
