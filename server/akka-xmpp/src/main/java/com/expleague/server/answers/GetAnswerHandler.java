package com.expleague.server.answers;

import akka.http.javadsl.model.*;
import akka.japi.Option;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.spbsu.commons.io.StreamTools;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;

import javax.jcr.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 18.04.17.
 */
public class GetAnswerHandler extends ActorAdapter {
  private static final Logger log = Logger.getLogger(GetAnswerHandler.class.getName());
  private final Session session;

  public GetAnswerHandler(Session session) {
    this.session = session;
  }

  @ActorMethod
  public void onRequest(final HttpRequest request) {
    final Query query = request.getUri().query();
    final Option<String> optId = query.get("id");
    if (optId.isEmpty()) {
      reply(HttpResponse.create().withStatus(404));
      return;
    }
    final String id = optId.get();
    try {
      final Node node = session.getNodeByIdentifier(id); // answer node
      final Property property = node.getProperty(Property.JCR_DATA);
      final Binary binary = property.getBinary();
      final HtmlRenderer renderer = HtmlRenderer.builder().build();
      final Parser parser = Parser.builder().build();
      final String result = renderer.render(parser.parseReader(new InputStreamReader(binary.getStream(), StreamTools.UTF)));
      reply(HttpResponse.create().withStatus(200).withEntity(
          MediaTypes.TEXT_HTML.toContentType(HttpCharsets.UTF_8),
          "<html><title>" + node.getParent().getProperty("topic") + "</title><body>\"" + result + "\"</body></html>"));
    } catch (RepositoryException | IOException e) {
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
}
