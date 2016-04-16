package com.expleague.server;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.http.javadsl.Http;
import akka.http.javadsl.IncomingConnection;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.http.javadsl.model.headers.AccessControlAllowHeaders;
import akka.http.javadsl.model.headers.AccessControlAllowMethods;
import akka.http.javadsl.model.headers.AccessControlAllowOrigin;
import akka.http.javadsl.model.headers.HttpOriginRange;
import akka.japi.function.Function;
import akka.japi.function.Predicate;
import akka.japi.function.Procedure;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.Materializer;
import akka.stream.Supervision;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.Timeout;
import com.expleague.util.akka.*;
import com.expleague.xmpp.BoshBody;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.Stream;
import com.expleague.xmpp.stanza.Stanza;
import scala.Option;
import scala.concurrent.Future;

import javax.xml.bind.Unmarshaller;
import java.io.PipedInputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 24.12.15
 * Time: 14:47
 */
public class BOSHServer extends ActorAdapter<UntypedActor> {
  private static final Logger log = Logger.getLogger(BOSHServer.class.getName());
  private Materializer materializer;
  @Override
  public void preStart() throws Exception {
    final ActorMaterializerSettings settings = ActorMaterializerSettings.create(context().system())
        .withSupervisionStrategy((Function<Throwable, Supervision.Directive>) param -> {
          log.log(Level.SEVERE, "Exception in the BOSH protocol flow", param);
          return Supervision.stop();
        })
        .withDebugLogging(true)
        .withInputBuffer(1 << 6, 1 << 7);
    materializer = ActorMaterializer.create(settings, context());
    final Source<IncomingConnection, Future<ServerBinding>> serverSource = Http.get(context().system()).bind("localhost", 5280, materializer);
//    final Source<IncomingConnection, Future<ServerBinding>> serverSource = Http.get(context().system()).bind("192.168.1.3", 5280, materializer);
    serverSource.to(Sink.foreach(new ProcessConnection())).run(materializer);
  }

  private class ProcessConnection implements Procedure<IncomingConnection> {
    @Override
    public void apply(IncomingConnection connection) throws Exception {
//      log.fine("Accepted new BOSH connection from " + connection.remoteAddress());
      connection.handleWith(Flow.of(HttpRequest.class).filter(new Predicate<HttpRequest>() {
        int index = 0;
        @Override
        public boolean test(HttpRequest httpRequest) {
          System.out.println(httpRequest);
          return true;
        }
      }).map(request -> {
        if (request.method() == HttpMethods.OPTIONS)
          return HttpResponse.create()
              .addHeader(AccessControlAllowOrigin.create(HttpOriginRange.ALL))
              .addHeader(AccessControlAllowMethods.create(HttpMethods.OPTIONS, HttpMethods.POST, HttpMethods.GET))
              .addHeader(AccessControlAllowHeaders.create("Content-Type"))
              .withStatus(204);
        if (request.method() != HttpMethods.POST)
          return HttpResponse.create().withStatus(404);

        HttpResponse response = HttpResponse.create();
        response = response.addHeader(AccessControlAllowOrigin.create(HttpOriginRange.ALL));
        try {
          PipedInputStream pis = new PipedInputStream();
          final ActorRef pipe = context().actorOf(ActorContainer.props(StreamPipe.class, pis));
          AkkaTools.ask(pipe, new StreamPipe.Open());
          request.entity().getDataBytes().to(Sink.actorRef(pipe, new StreamPipe.Close())).run(materializer);
          final BoshBody boshBody;
          { // incomming
            final Unmarshaller unmarshaller = Stream.jaxb().createUnmarshaller();
            boshBody = (BoshBody) unmarshaller.unmarshal(pis);
            log.finest("BOSH> " + boshBody);
          }
          if (boshBody.sid() == null) {
            final String sid = Stanza.generateId().replace('/', 'b');
            boshBody.sid(sid);
            boshBody.requests(3);
            context().actorOf(ActorContainer.props(BOSHSession.class), sid);
          }
          else { // synchronous form
            final Option<ActorRef> sessionOpt = context().child(boshBody.sid());
            if (sessionOpt.isDefined()) {
              final ActorRef session = sessionOpt.get();
              final List<Item> contents = AkkaTools.ask(session, boshBody, Timeout.apply(1, TimeUnit.HOURS));
              boshBody.items().clear();
              if (contents != null && !contents.isEmpty()) {
                boshBody.items().addAll(contents);
              }
            }
            else boshBody.type("terminate");
          }
          { // outgoing
            final ResponseEntity entity = HttpEntities.create(
                ContentTypes.create(MediaTypes.TEXT_XML, HttpCharsets.UTF_8),
                boshBody.xmlString(true)
            );
//          response.addHeader(Len)
            response = response.withEntity(entity);
            log.finest("BOSH< " + boshBody);
          }
        }
        catch (Exception e) {
          log.log(Level.WARNING, "Exception during BOSH processing", e);
          response = response.withStatus(500).withEntity(
              ContentTypes.TEXT_HTML_UTF8,
              "<html><body>" + e.toString() + "</body></html>");

        }
        return response;
      }), materializer);
    }
  }
}
