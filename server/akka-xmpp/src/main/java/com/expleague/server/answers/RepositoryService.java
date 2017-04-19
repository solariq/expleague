package com.expleague.server.answers;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.UntypedActor;
import akka.dispatch.Futures;
import akka.http.javadsl.Http;
import akka.http.javadsl.IncomingConnection;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.japi.function.Function;
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
import org.jetbrains.annotations.Nullable;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import javax.jcr.*;
import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 17.04.17.
 */
public class RepositoryService extends ActorAdapter<UntypedActor> {
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

    final Source<IncomingConnection, Future<ServerBinding>> serverSource = Http.get(context().system()).bind("0.0.0.0", 8033, materializer);
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
    connection.handleWithAsyncHandler((Function<HttpRequest, Future<HttpResponse>>) httpRequest -> {
      if (readSession == null)
        return Futures.successful(HttpResponse.create().withStatus(404));
      final Uri uri = httpRequest.getUri();
      final ActorRef handler;
      if (uri.path().endsWith("/search")) {
        handler = context().actorOf(props(SearchRequestHandler.class, readSession));
      }
      else if (uri.path().endsWith("/get")) {
        handler = context().actorOf(props(GetAnswerHandler.class, readSession));
      }
      else if (uri.path().equals("/")) {
        return Futures.successful(HttpResponse.create().withEntity(
            MediaTypes.TEXT_HTML.toContentType(HttpCharsets.UTF_8),
            "<html><body><form enctype=\"multipart/form-data\" action=\".\" method=\"post\" type=><input name=\"id\" type=\"text\"><input name=\"image\" accept=\"image/jpeg\" type=\"file\" alt=\"Submit\"><input type=\"submit\"></form></body></html>"));
      }
      else return Futures.successful(HttpResponse.create().withStatus(404));

      final Future ask = Patterns.ask(handler, httpRequest, Timeout.apply(Duration.create(10, TimeUnit.MINUTES)));
      //noinspection unchecked
      return (Future<HttpResponse>) ask;
    }, materializer);

  }

  @ActorMethod
  public void onMessage(Message msg) {
    if (writeSession == null || !msg.has(Offer.class)) // won't index incomplete message
      return;
    final Offer offer = msg.get(Offer.class);
    try {
      final Node offerNode = findOffer(offer);
      if (msg.has(Answer.class)) {
        final Answer answer = msg.get(Answer.class);
        final Node answerNode;
        if (!offerNode.hasNode("answer")) {
          answerNode = offerNode.addNode("answer", "nt:resource");
        }
        else answerNode = offerNode.getNode("answer");
        answerNode.setProperty("jcr:mimeType", "text/markdown");
        answerNode.setProperty("jcr:data", writeSession.getValueFactory().createBinary(new ByteArrayInputStream(answer.value().getBytes(StreamTools.UTF))));
        answerNode.setProperty("jcr:encoding", "UTF-8");
      }
      if (msg.has(Operations.Verified.class)) {
        offerNode.setProperty("verified", msg.get(Operations.Verified.class).authority().local());
      }
      if (msg.has(Operations.Feedback.class)) {
        offerNode.setProperty("feedback", msg.get(Operations.Feedback.class).stars());
      }
      writeSession.save();
    }
    catch (RepositoryException re) {
      log.log(Level.WARNING, "JCR exception onMessage", re);
    }
  }

  private void setupOffer(Node partNode, Offer offer) throws RepositoryException {
    partNode.setProperty("topic", offer.topic());
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
    { // tags & patterns cleanup
      final NodeIterator tagsIt = partNode.getNodes();
      while (tagsIt.hasNext()) {
        final Node node = tagsIt.nextNode();
        if ("tag".equals(node.getName()) || "pattern".equals(node.getName()))
          node.remove();
      }
    }
    { // tags
      for (final Tag tag: offer.tags())
        partNode.addNode("tag").setProperty("name", tag.name());
    }

    { // patterns
      for (final Pattern pattern: offer.patterns())
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

  public static JID jid() {
    return XMPP.jid(ID);
  }
}
