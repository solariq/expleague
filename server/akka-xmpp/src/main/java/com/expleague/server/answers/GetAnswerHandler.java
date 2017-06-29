package com.expleague.server.answers;

import akka.http.javadsl.model.*;
import akka.japi.Option;
import com.expleague.util.akka.ActorMethod;
import com.spbsu.commons.io.StreamTools;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;

import javax.jcr.*;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Experts League
 * Created by solar on 18.04.17.
 */
public class GetAnswerHandler extends WebHandler {
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
    final boolean showMd = query.get("md").isDefined() && Boolean.parseBoolean(query.get("md").get());
    try {
      final String result;
      final String title;
      final Node node = session.getNodeByIdentifier(id); // answer node
      if ("answer-text".equals(node.getName()) || "topic".equals(node.getName())) {
        final NodeIterator nodeIterator;
        if ("answer-text".equals(node.getName()))
          nodeIterator = node.getParent().getParent().getNodes();
        else
          nodeIterator = node.getParent().getNode("answers").getNodes();

        final StringBuilder stringBuilder = new StringBuilder();
        int answerNum = 0;
        while (nodeIterator.hasNext()) {
          final Node answerTextNode = nodeIterator.nextNode().getNode("answer-text");
          final Property answerProp = answerTextNode.getProperty(Property.JCR_DATA);
          final String answer = showMd ? answerProp.getString() : extractAnswer(answerProp);
          if (answerNum > 0)
            stringBuilder.append("<br/>");

          ++answerNum;
          stringBuilder.append("<p><h2>")
              .append("<a name=\"")
              .append("answer")
              .append(answerNum)
              .append("\">")
              .append("Ответ №")
              .append(answerNum)
              .append("</a></h2></p>")
              .append(showMd ? textAreaWithParams() + answer + "</textarea>" : answer);
        }
        result = stringBuilder.toString();
        if ("answer-text".equals(node.getName())) {
          if (node.getParent().getParent().getParent().hasProperty("topic"))
            title = node.getParent().getParent().getParent().getProperty("topic").getString();
          else
            title = node.getParent().getParent().getParent().getNode("topic").getProperty(Property.JCR_DATA).getString();
        }
        else {
          title = node.getProperty(Property.JCR_DATA).getString();
        }
      }
      else { //old version
        final Property answerProp = node.getProperty(Property.JCR_DATA);
        result = showMd ? textAreaWithParams() + answerProp.getString() + "</textarea>" : extractAnswer(answerProp);
        title = node.getParent().getProperty("topic").getString();
      }
      reply(HttpResponse.create().withStatus(200).withEntity(
          MediaTypes.TEXT_HTML.toContentType(HttpCharsets.UTF_8),
          "<html><title>" + title + "</title><body>" + result + "</body></html>"));
    } catch (RepositoryException | IOException e) {
      handleException(e);
    }
  }

  private static String extractAnswer(Property property) throws RepositoryException, IOException {
    final Binary binary = property.getBinary();
    final HtmlRenderer renderer = HtmlRenderer.builder().build();
    final Parser parser = Parser.builder().build();
    return renderer.render(parser.parseReader(new InputStreamReader(binary.getStream(), StreamTools.UTF)));
  }

  private static String textAreaWithParams() {
    return "<textarea cols=\"100\" rows=\"20\">";
  }
}
