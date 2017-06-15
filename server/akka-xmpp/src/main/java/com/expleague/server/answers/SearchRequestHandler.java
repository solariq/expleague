package com.expleague.server.answers;

import akka.actor.UntypedActor;
import akka.http.javadsl.model.*;
import com.expleague.server.admin.ExpLeagueAdminService;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spbsu.commons.io.StreamTools;
import com.typesafe.config.Config;
import org.jetbrains.annotations.Nullable;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 18.04.17.
 */
public class SearchRequestHandler extends ActorAdapter<UntypedActor> {
  private static final Logger log = Logger.getLogger(SearchRequestHandler.class.getName());
  private final static ObjectMapper mapper = new ExpLeagueAdminService.DefaultJsonMapper();
  private final Session session;
  @Nullable
  private static Config config;

  public SearchRequestHandler(Session session) {
    this.session = session;
  }

  @ActorMethod
  public void onRequest(final HttpRequest request) throws Exception {
    final Uri uri = request.getUri();

    final String text = uri.query().get("text").getOrElse("");
    if (text.isEmpty()) {
      reply(HttpResponse.create().withEntity(
          MediaTypes.TEXT_HTML.toContentType(HttpCharsets.UTF_8),
          "<body>Чо, все документы тебе показать?! Ага, ща.</body>"));
      return;
    }

    final int offset = Integer.parseInt(uri.query().get("startIndex").getOrElse("0"));
    final int limit = config != null ? config.getInt("results-per-page") : 10;
    try {
      final QueryManager manager = session.getWorkspace().getQueryManager();
      final javax.jcr.query.Query query = manager.createQuery("//element(*, nt:resource)[jcr:contains(., '" + text + "')]", javax.jcr.query.Query.XPATH);
      query.setOffset(offset);
      query.setLimit(limit);
      final QueryResult result = query.execute();
      final NodeIterator nodeIterator = result.getNodes();

      final Map<String, Object> map = new HashMap<>();
      map.put("resultsPerPage", limit);
      final List<SearchItemDto> searchItems = new ArrayList<>();
      while (nodeIterator.hasNext()) {
        final Node node = nodeIterator.nextNode();
        final Uri answerUri = Uri.create(uri.scheme() + "://" + uri.host() + "/get?id=" + node.getIdentifier()).port(uri.port());
        final String topic;
        if ("answer-text".equals(node.getName()))
          topic = node.getParent().getParent().getParent().getProperty("topic").getString();
        else //old version
          topic = node.getParent().getProperty("topic").getString();

        searchItems.add(new SearchItemDto(topic, answerUri.toString()));
      }
      map.put("items", searchItems);
      reply(HttpResponse.create().withStatus(200).withEntity(ContentTypes.APPLICATION_JSON, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map)));
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

  public static void setConfig(Config value) {
    config = value;
  }

  @SuppressWarnings("unused")
  private static class SearchItemDto {
    @JsonProperty
    private final String link;
    @JsonProperty
    private final String topic;

    public SearchItemDto(String topic, String link) {
      this.topic = topic;
      this.link = link;
    }
  }
}
