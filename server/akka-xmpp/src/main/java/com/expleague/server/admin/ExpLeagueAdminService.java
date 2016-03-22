package com.expleague.server.admin;

import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;
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
import com.expleague.server.Roster;
import com.expleague.server.admin.dto.ExpertsProfileDto;
import com.expleague.server.admin.dto.JIDDto;
import com.expleague.server.admin.dto.OrderDto;
import com.expleague.server.agents.ExpLeagueOrder;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.dao.Archive;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Charsets;
import com.typesafe.config.Config;
import org.jetbrains.annotations.NotNull;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author vpdelta
 */
public class ExpLeagueAdminService extends UntypedActor {
  private static final Logger log = Logger.getLogger(ExpLeagueAdminService.class.getName());

  private final static ObjectMapper mapper = new DefaultJsonMapper();

  private Config config;
  private Materializer materializer;

  public ExpLeagueAdminService(final Config config) {
    this.config = config;
  }

  @Override
  public void preStart() throws Exception {
    materializer = ActorMaterializer.create(context());
    final int port = config.getInt("port");
    final Source<IncomingConnection, Future<ServerBinding>> serverSource = Http.get(context().system()).bind("0.0.0.0", port, materializer);
    serverSource.to(Sink.actorRef(self(), PoisonPill.getInstance())).run(materializer);
    log.fine("Started on port: " + port);
  }

  @Override
  public void onReceive(Object o) throws Exception {
    if (o instanceof IncomingConnection) {
      final IncomingConnection connection = (IncomingConnection) o;
      log.fine("Accepted new connection from " + connection.remoteAddress());
      connection.handleWithAsyncHandler((Function<HttpRequest, Future<HttpResponse>>) httpRequest -> {
        final Future ask = (Future) Patterns.ask(context().actorOf(Props.create(Handler.class)), httpRequest, Timeout.apply(Duration.create(10, TimeUnit.MINUTES)));
        //noinspection unchecked
        return (Future<HttpResponse>)ask;
      }, materializer);
    }
    else unhandled(o);
  }

  public static class Handler extends UntypedActor {
    @Override
    public void onReceive(final Object message) throws Exception {
      if (message instanceof HttpRequest) {
        final HttpRequest request = (HttpRequest) message;
        final String path = request.getUri().path();
        log.fine(request.method() + " " + path);
        final LaborExchange.Board board = LaborExchange.board();
        HttpResponse response = HttpResponse.create().withStatus(404).withEntity("Page not found");
        if (request.method() == HttpMethods.GET) {
          try {
            if (path.isEmpty() || "/".equals(path)) {
              final File file = new File("admin/static/index.html");
              response = HttpResponse.create().withStatus(200).withEntity(HttpEntities.create(ContentTypes.TEXT_HTML_UTF8, file));
            }
            else if (path.startsWith("/static")) {
              final File file = new File("admin/static", path.substring(path.indexOf("static") + 7));
              if (file.isFile()) {
                response = HttpResponse.create().withStatus(200).withEntity(HttpEntities.create(getContentType(file), file));
              }
              else {
                response = HttpResponse.create().withStatus(404).withEntity("File not found");
              }
            }
            else if ("/open".equals(path)) {
              response = getOrders(board.open());
            }
            else if ("/closed/without/feedback".equals(path)) {
              response = getOrders(board.orders(
                new LaborExchange.OrderFilter(true, EnumSet.of(ExpLeagueOrder.Status.DONE))
              ));
            }
            else if ("/closed".equals(path)) {
              response = getOrders(board.orders(
                new LaborExchange.OrderFilter(false, EnumSet.of(ExpLeagueOrder.Status.DONE))
              ));
            }
            else if ("/top/experts".equals(path)) {
              final List<ExpertsProfileDto> experts = board.topExperts()
                .map(Roster.instance()::profile)
                .map(ExpertsProfileDto::new)
                .collect(Collectors.toList());
              response = getJsonResponse("experts", experts);
            }
            else if (path.startsWith("/history/")) {
              final String roomId = path.substring("/history/".length());
              response = getOrders(Stream.of(board.history(roomId)));
            }
            else if (path.startsWith("/active/")) {
              final String roomId = path.substring("/active/".length());
              response = getOrders(Stream.of(board.active(roomId)));
            }
            else if (path.startsWith("/related/")) {
              final JID jid = JID.parse(path.substring("/related/".length()));
              response = getOrders(board.related(jid));
            }
            else if (path.startsWith("/dump/")) {
              final JID jid = JID.parse(path.substring("/dump/".length()));
              final Archive.Dump dump = Archive.instance().dump(jid.local());
              final List<String> messages = dump.stream().map(Item::xmlString).collect(Collectors.toList());
              response = getJsonResponse("messages", messages);
            }
          } catch (Exception e) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(out));
            final String stacktrace = new String(out.toByteArray(), Charsets.UTF_8);
            response = HttpResponse.create().withStatus(500).withEntity(stacktrace);
            log.warning(stacktrace);
          }
        }
        sender().tell(response, self());
      }
    }

    protected HttpResponse getOrders(Stream<ExpLeagueOrder> stream) throws JsonProcessingException {
      final List<OrderDto> history = stream.map(OrderDto::new).collect(Collectors.toList());
      Collections.reverse(history);
      return getJsonResponse("orders", history);
    }

    protected HttpResponse getJsonResponse(final String name, final Object value) throws JsonProcessingException {
      final Map<String, Object> map = new HashMap();
      map.put(name, value);
      return HttpResponse.create().withStatus(200).withEntity(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map));
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
  }

  public static class DefaultJsonMapper extends ObjectMapper {
    public DefaultJsonMapper() {
      this.setTimeZone(TimeZone.getDefault());
      this.configure(SerializationFeature.INDENT_OUTPUT, false);
      this.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
      this.configure(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS, false);
      this.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);
      this.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
      this.configure(SerializationFeature.INDENT_OUTPUT, true);
      this.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
  }
}
