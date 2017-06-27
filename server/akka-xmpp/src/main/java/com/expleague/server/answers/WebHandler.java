package com.expleague.server.answers;

import akka.http.javadsl.model.HttpCharsets;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.MediaTypes;
import com.expleague.util.akka.ActorAdapter;
import com.spbsu.commons.io.StreamTools;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: Artem
 * Date: 26.06.2017
 */
public class WebHandler extends ActorAdapter {
  private static final Logger log = Logger.getLogger(WebHandler.class.getName());

  protected void handleException(Exception e) {
    log.log(Level.WARNING, "Unable to login to jackrabbit repository", e);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    e.printStackTrace(new PrintStream(out));
    String trace = new String(out.toByteArray(), StreamTools.UTF);
    trace = trace.replace("\n", "<br/>\n");
    reply(HttpResponse.create().withStatus(503).withEntity(
        MediaTypes.TEXT_HTML.toContentType(HttpCharsets.UTF_8),
        "<html><body>Exception while request processing: <br/>" + trace + "</body></html>"));
  }
}
