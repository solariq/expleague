package com.expleague.server.answers;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.IncomingConnection;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.http.javadsl.settings.ServerSettings;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.Timeout;
import com.expleague.model.*;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.agents.XMPP;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.spbsu.commons.io.StreamTools;
import com.spbsu.commons.math.MathTools;
import org.apache.jackrabbit.commons.JcrUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import javax.jcr.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 17.04.17.
 */
public class RepositoryService extends ActorAdapter<AbstractActor> {
  private static final Logger log = Logger.getLogger(RepositoryService.class.getName());
  public static final String ID = "repository";
  private Materializer materializer;
  private Repository repository;
  @Nullable
  private Session writeSession;
  @Nullable
  private Session readSession;

  private RepositoryService() {
    try {
      repository = JcrUtils.getRepository();
    } catch (RepositoryException e) {
      log.warning("Unable to start jackrabbit repository");
    }
  }

  @Override
  public void preStart() throws Exception {
//    super.preStart();
    materializer = ActorMaterializer.create(context());

    final Source<IncomingConnection, CompletionStage<ServerBinding>> serverSource = Http.get(context().system()).bind(ConnectHttp.toHost("0.0.0.0", 8033), ServerSettings.create(context().system()));
    serverSource.to(Sink.actorRef(self(), PoisonPill.getInstance())).run(materializer);
    if (!ExpLeagueServer.config().unitTest()) {
      writeSession = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
      readSession = repository.login();
    }
  }

  @Override
  protected void postStop() {
    if (readSession != null)
      readSession.logout();
    if (writeSession != null)
      writeSession.logout();
    super.postStop();
  }

  @ActorMethod
  public void onConnection(IncomingConnection connection) {
    log.fine("Accepted new connection from " + connection.remoteAddress());
    connection.handleWithAsyncHandler(httpRequest -> {
      if (readSession == null)
        return CompletableFuture.completedFuture(HttpResponse.create().withStatus(404));
      final Uri uri = httpRequest.getUri();
      final ActorRef handler;
      if (uri.path().endsWith("/search")) {
        handler = context().actorOf(props(SearchRequestHandler.class, readSession));
      }
      else if (uri.path().endsWith("/get")) {
        handler = context().actorOf(props(GetAnswerHandler.class, readSession));
      }
      else if (uri.path().startsWith("/static")) {
        final File file = new File("search/static", uri.path().substring(uri.path().indexOf("static") + 7));
        if (file.isFile()) {
          return CompletableFuture.completedFuture(HttpResponse.create().withStatus(200).withEntity(HttpEntities.create(getContentType(file), file)));
        }
        else {
          return CompletableFuture.completedFuture(HttpResponse.create().withStatus(404).withEntity("File not found"));
        }
      }
      else if (uri.path().equals("/")) {
        final File file = new File("search/static/index.html");
        return CompletableFuture.completedFuture(HttpResponse.create().withStatus(200).withEntity(HttpEntities.create(ContentTypes.TEXT_HTML_UTF8, file)));
      }
      else return CompletableFuture.completedFuture(HttpResponse.create().withStatus(404));

      final Future ask = Patterns.ask(handler, httpRequest, Timeout.apply(Duration.create(10, TimeUnit.MINUTES)));
      //noinspection unchecked
      return FutureConverters.toJava(ask);
    }, materializer);

  }

  @ActorMethod
  public void onMessage(Message msg) {
    if (writeSession == null || !msg.has(Offer.class)) // won't index incomplete message
      return;
    final Offer offer = msg.get(Offer.class);
    if (offer.room() == null) //ignore old rooms
      return;
    try {
      final Node offerNode = findOffer(offer);
      if (msg.has(Answer.class)) {
        final Answer answer = msg.get(Answer.class);
        final String answerText = answer.value();
        final int firstLineEnd = answerText.indexOf('\n');
        final String shortAnswer = firstLineEnd >= 0 ? answerText.substring(0, firstLineEnd) : answerText;
        if (firstLineEnd >= 0) {
          final String fullAnswer = answerText.substring(firstLineEnd + 1);
          final Node answersNode;
          if (!offerNode.hasNode("answers")) {
            answersNode = offerNode.addNode("answers");
          }
          else answersNode = offerNode.getNode("answers");

          final String order = answer.order();
          final Node answerForOrder;
          if (answersNode.hasNode(order)) {
            answerForOrder = answersNode.getNode(order);
          }
          else {
            answerForOrder = answersNode.addNode(order);
            answerForOrder.setProperty("answer-num", answersNode.getNodes().getSize());
          }

          answerForOrder.setProperty("short-answer", shortAnswer);
          answerForOrder.setProperty("difficulty", answer.difficulty());
          answerForOrder.setProperty("success", answer.success());
          answerForOrder.setProperty("specifications", answer.specifications());

          final Node answerTextNode;
          if (answerForOrder.hasNode("answer-text"))
            answerTextNode = answerForOrder.getNode("answer-text");
          else
            answerTextNode = answerForOrder.addNode("answer-text", "nt:resource");

          answerTextNode.setProperty("jcr:mimeType", "text/markdown");
          answerTextNode.setProperty("jcr:data", writeSession.getValueFactory().createBinary(new ByteArrayInputStream(fullAnswer.getBytes(StreamTools.UTF))));
          answerTextNode.setProperty("jcr:encoding", "UTF-8");

          { //remove old version if exists
            if (offerNode.hasNode("answer")) {
              offerNode.getNode("answer").remove();
            }
          }
        }
      }
      if (msg.has(Operations.Verified.class)) {
        offerNode.setProperty("verified", msg.get(Operations.Verified.class).authority().local());
      }
      if (msg.has(Operations.Feedback.class)) {
        offerNode.setProperty("feedback", msg.get(Operations.Feedback.class).stars());
      }
      writeSession.save();
    } catch (RepositoryException re) {
      log.log(Level.WARNING, "JCR exception onMessage", re);
    }
  }

  private void setupOffer(Node partNode, Offer offer) throws RepositoryException {
    assert writeSession != null;
    partNode.setProperty("started", offer.started());
    final Node locationNode;
    if (offer.location() != null) {
      if (!partNode.hasNode("location"))
        locationNode = partNode.addNode("location");
      else
        locationNode = partNode.getNode("location");
      locationNode.setProperty("latitude", offer.location().latitude());
      locationNode.setProperty("longitude", offer.location().longitude());
    }
    { //save topic to make index
      final Node topicNode;
      if (!partNode.hasNode("topic"))
        topicNode = partNode.addNode("topic", "nt:resource");
      else
        topicNode = partNode.getNode("topic");

      topicNode.setProperty("jcr:mimeType", "text/markdown");
      topicNode.setProperty("jcr:data", writeSession.getValueFactory().createBinary(new ByteArrayInputStream(offer.topic().getBytes(StreamTools.UTF))));
      topicNode.setProperty("jcr:encoding", "UTF-8");
    }
    { // tags & patterns cleanup
      final NodeIterator tagsIt = partNode.getNodes();
      while (tagsIt.hasNext()) {
        final Node node = tagsIt.nextNode();
        if ("tag".equals(node.getName()) || "pattern".equals(node.getName()))
          node.remove();
      }
    }
    { // tags
      for (final Tag tag : offer.tags())
        partNode.addNode("tag").setProperty("name", tag.name());
    }

    { // patterns
      for (final Pattern pattern : offer.patterns())
        partNode.addNode("pattern").setProperty("name", pattern.name());
    }
  }

  private Node findOffer(Offer offer) throws RepositoryException {
    assert writeSession != null;
    final Node rootNode = writeSession.getRootNode();
    final Node roomsNode;
    if (!rootNode.hasNode("rooms"))
      roomsNode = rootNode.addNode("rooms");
    else
      roomsNode = rootNode.getNode("rooms");
    final Node roomNode;
    final String roomId = offer.room().local();
    if (!roomsNode.hasNode(roomId)) {
      roomNode = roomsNode.addNode(roomId);
      roomNode.setProperty("owner", offer.client().local());
    }
    else roomNode = roomsNode.getNode(roomId);
    final NodeIterator nodes = roomNode.getNodes();
    Node partNode = null;
    while (nodes.hasNext()) {
      final Node next = nodes.nextNode();
      if (Math.abs(next.getProperty("started").getDouble() - offer.started()) < MathTools.EPSILON) {
        partNode = next;
        break;
      }
    }
    if (partNode == null)
      partNode = roomNode.addNode("part");

    setupOffer(partNode, offer);
    return partNode;
  }

  @NotNull
  protected ContentType getContentType(final File file) {
    final String fileName = file.getName();
    return fileName.endsWith(".js")
        ? ContentTypes.create(MediaTypes.APPLICATION_JSON)
        : fileName.endsWith(".css")
        ? ContentTypes.create(MediaTypes.TEXT_CSS, HttpCharsets.UTF_8)
        : fileName.endsWith(".png")
        ? ContentTypes.create(MediaTypes.IMAGE_PNG)
        : ContentTypes.create(MediaTypes.APPLICATION_OCTET_STREAM);
  }

  public static JID jid() {
    return XMPP.jid(ID);
  }
}
