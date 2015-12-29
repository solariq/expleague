package com.tbts.server;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.http.javadsl.Http;
import akka.http.javadsl.IncomingConnection;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.http.javadsl.model.headers.AccessControlAllowHeaders;
import akka.http.javadsl.model.headers.AccessControlAllowMethods;
import akka.http.javadsl.model.headers.AccessControlAllowOrigin;
import akka.http.javadsl.model.headers.HttpOriginRange;
import akka.japi.function.Procedure;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.Timeout;
import com.tbts.util.akka.AkkaTools;
import com.tbts.util.akka.StreamPipe;
import com.tbts.util.akka.UntypedActorAdapter;
import com.tbts.xmpp.BoshBody;
import com.tbts.xmpp.Item;
import com.tbts.xmpp.Stream;
import com.tbts.xmpp.stanza.Stanza;
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
public class BOSHServer extends UntypedActorAdapter {
  private static final Logger log = Logger.getLogger(BOSHServer.class.getName());
  private Materializer materializer;
  @Override
  public void preStart() throws Exception {
    materializer = ActorMaterializer.create(context());
    final Source<IncomingConnection, Future<ServerBinding>> serverSource = Http.get(context().system()).bind("localhost", 5280, materializer);
    serverSource.to(Sink.foreach(new ProcessConnection())).run(materializer);
  }

  private class ProcessConnection implements Procedure<IncomingConnection> {
    @Override
    public void apply(IncomingConnection connection) throws Exception {
      System.out.println("Accepted new BOSH connection from " + connection.remoteAddress());
      connection.handleWith(Flow.of(HttpRequest.class).take(1).map(request -> {
        if (request.method() == HttpMethods.OPTIONS)
          return HttpResponse.create()
              .addHeader(AccessControlAllowOrigin.create(HttpOriginRange.ALL))
              .addHeader(AccessControlAllowMethods.create(HttpMethods.OPTIONS, HttpMethods.POST, HttpMethods.GET))
              .addHeader(AccessControlAllowHeaders.create("text/xml"))
              .withStatus(204);
        if (request.method() != HttpMethods.POST)
          return HttpResponse.create().withStatus(404);

        HttpResponse response = HttpResponse.create();
        response = response.addHeader(AccessControlAllowOrigin.create(HttpOriginRange.ALL));
        try {
          PipedInputStream pis = new PipedInputStream();
          final ActorRef pipe = context().actorOf(Props.create(StreamPipe.class, pis));
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
            context().actorOf(Props.create(BOSHSession.class, materializer), sid);
          }
          else { // synchronous form
            final Option<ActorRef> sessionOpt = context().child(boshBody.sid());
            if (sessionOpt.isDefined()) {
              final ActorRef session = sessionOpt.get();
              final List<Item> contents = AkkaTools.ask(session, boshBody, Timeout.apply(1, TimeUnit.HOURS));
              if (contents != null && !contents.isEmpty()) {
                boshBody.items().clear();
                boshBody.items().addAll(contents);
              }
            }
            else {
              boshBody.type("terminate");
            }
          }
          { // outgoing
            final ResponseEntity entity = HttpEntities.create(
                ContentType.create(MediaTypes.lookup("text", "xml").get(), HttpCharsets.UTF_8),
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
              MediaTypes.TEXT_HTML.toContentType(),
              "<html><body>" + e.toString() + "</body></html>");

        }
        return response;
      }), materializer);
    }
  }
}
