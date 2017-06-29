package com.expleague.server.answers;

import akka.http.javadsl.model.*;
import com.expleague.server.admin.ExpLeagueAdminService;
import com.expleague.util.akka.ActorMethod;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import org.jetbrains.annotations.Nullable;

import javax.jcr.*;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Experts League
 * Created by solar on 18.04.17.
 */
public class SearchRequestHandler extends WebHandler {
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
      //noinspection deprecation
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
        final StringBuilder uriSb = new StringBuilder();
        uriSb.append(uri.scheme()).append("://").append(uri.host()).append("/get?id=").append(node.getIdentifier());
        final StringBuilder mdUriSb = new StringBuilder(uriSb);
        mdUriSb.append("&md=true");

        final String topic;
        if ("answer-text".equals(node.getName())) {
          final StringBuilder topicSb = new StringBuilder();
          if (node.getParent().getParent().getParent().hasProperty("topic"))
            topicSb.append(node.getParent().getParent().getParent().getProperty("topic").getString());
          else
            topicSb.append(node.getParent().getParent().getParent().getNode("topic").getProperty(Property.JCR_DATA).getString());

          final long answerNum = node.getParent().getProperty("answer-num").getLong();
          topicSb.append(" (Ответ №").append(answerNum).append(")");
          uriSb.append("#answer").append(answerNum);
          mdUriSb.append("#answer").append(answerNum);
          topic = topicSb.toString();
        }
        else if ("topic".equals(node.getName())) {
          topic = node.getProperty(Property.JCR_DATA).getString();
        }
        else { //old version
          topic = node.getParent().getProperty("topic").getString();
        }

        final Uri answerUri = Uri.create(uriSb.toString()).port(uri.port());
        final Uri mdAnswerUri = Uri.create(mdUriSb.toString()).port(uri.port());
        searchItems.add(new SearchItemDto(topic, answerUri.toString(), mdAnswerUri.toString()));
      }
      map.put("items", searchItems);
      reply(HttpResponse.create().withStatus(200).withEntity(ContentTypes.APPLICATION_JSON, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map)));
    } catch (RepositoryException e) {
      handleException(e);
    }
  }

  public static void setConfig(@Nullable Config value) {
    config = value;
  }

  @SuppressWarnings("unused")
  private static class SearchItemDto {
    @JsonProperty
    private final String link;
    @JsonProperty
    private final String mdLink;
    @JsonProperty
    private final String topic;

    public SearchItemDto(String topic, String link, String mdLink) {
      this.topic = topic;
      this.link = link;
      this.mdLink = mdLink;
    }
  }
}
