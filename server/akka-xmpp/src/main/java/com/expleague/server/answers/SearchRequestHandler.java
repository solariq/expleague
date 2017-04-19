package com.expleague.server.answers;

import akka.actor.UntypedActor;
import akka.http.javadsl.model.*;
import akka.japi.Option;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.spbsu.commons.io.StreamTools;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 18.04.17.
 */
public class SearchRequestHandler extends ActorAdapter<UntypedActor> {
  private static final Logger log = Logger.getLogger(SearchRequestHandler.class.getName());
  private final Session session;

  public SearchRequestHandler(Session session) {
    this.session = session;
  }

  @ActorMethod
  public void onRequest(final HttpRequest request) throws Exception {
    Uri uri = request.getUri();

    final String text = uri.query().get("text").getOrElse("");
    if (text.isEmpty()) {
      reply(HttpResponse.create().withEntity(
          MediaTypes.TEXT_HTML.toContentType(HttpCharsets.UTF_8),
          "<body>Чо, все документы тебе показать?! Ага, ща.</body>"));
      return;
    }

    try {
      final QueryManager manager = session.getWorkspace().getQueryManager();
      final javax.jcr.query.Query query = manager.createQuery("//element(*, nt:resource)[jcr:contains(., '" + text + "')]", javax.jcr.query.Query.XPATH);
      final QueryResult result = query.execute();
      final NodeIterator nodeIterator = result.getNodes();
      final StringBuilder answer = new StringBuilder();
      answer.append("<html><title>По [").append(text).append("] нашлось ").append(nodeIterator.getSize()).append(" ответов</title>");
      answer.append("<body>");
      answer.append("<ol>");
      while (nodeIterator.hasNext()) {
        final Node node = nodeIterator.nextNode();
        final Uri answerUri = Uri.create(uri.scheme() + "://" + uri.host() + "/get?id=" + node.getIdentifier());
        answer.append("<li>");
        answer.append("<a href=\"").append(answerUri.toString()).append("\">")
            .append(node.getParent().getProperty("topic").toString())
            .append("</a>");
        answer.append("</li>");
      }
      answer.append("</ol>");
      answer.append("</body></html>");
      reply(HttpResponse.create().withStatus(200).withEntity(
          MediaTypes.TEXT_HTML.toContentType(HttpCharsets.UTF_8), answer.toString()
      ));
    } catch (RepositoryException e) {
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
