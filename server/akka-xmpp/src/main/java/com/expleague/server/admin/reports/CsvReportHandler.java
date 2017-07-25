package com.expleague.server.admin.reports;

import com.expleague.util.akka.ActorAdapter;

import java.util.Arrays;

/**
 * User: Artem
 * Date: 24.07.2017
 */
public class CsvReportHandler extends ActorAdapter {
  private final StringBuilder stringBuilder = new StringBuilder();
  private boolean firstCol = true;

  protected void headers(String... headers) {
    Arrays.stream(headers).forEach(this::column);
    newLine();
  }

  protected void row(String... values) {
    Arrays.stream(values).forEach(this::column);
    newLine();
  }

  protected void column(String value) {
    if (!firstCol)
      stringBuilder.append(",");
    stringBuilder.append(value);
    firstCol = false;
  }

  protected void newLine() {
    stringBuilder.append("\n");
    firstCol = true;
  }

  protected String build() {
    return stringBuilder.toString();
  }
}
