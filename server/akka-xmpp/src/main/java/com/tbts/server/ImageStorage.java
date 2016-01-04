package com.tbts.server;

import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.http.javadsl.Http;
import akka.http.javadsl.IncomingConnection;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.http.scaladsl.model.MultipartMediaType;
import akka.japi.JavaPartialFunction;
import akka.japi.Option;
import akka.japi.function.Function;
import akka.japi.function.Procedure;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import akka.util.Timeout;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.spbsu.commons.io.StreamTools;
import org.apache.commons.fileupload.MultipartStream;
import org.apache.commons.fileupload.ParameterParser;
import org.apache.commons.io.output.ByteArrayOutputStream;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.io.File;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 24.11.15
 * Time: 17:42
 */
public class ImageStorage extends UntypedActor {
  private static final Logger log = Logger.getLogger(ImageStorage.class.getName());
  private static final String BUCKET_NAME = "tbts-image-storage-main-chunk";
  final AmazonS3Client s3Client;
  private Materializer materializer;

  private ImageStorage() {
    final BasicAWSCredentials credentials = new BasicAWSCredentials("AKIAJPLJBHVNFAWY3S4A", "UEnvfQ2ver5mlOu7IJsjxRH3G9uF3/f0WNLFZ9c6");
    System.setProperty("com.amazonaws.services.s3.disableGetObjectMD5Validation", "true");
    s3Client = new AmazonS3Client(credentials);
    if (!s3Client.listBuckets().stream().anyMatch(bucket -> BUCKET_NAME.equals(bucket.getName()))) {
      s3Client.createBucket(BUCKET_NAME);
    }
  }

  @Override
  public void preStart() throws Exception {
    materializer = ActorMaterializer.create(context());
    final Source<IncomingConnection, Future<ServerBinding>> serverSource = Http.get(context().system()).bind("localhost", 8067, materializer);
    serverSource.to(Sink.actorRef(self(), PoisonPill.getInstance())).run(materializer);
  }

  @Override
  public void onReceive(Object o) throws Exception {
    if (o instanceof IncomingConnection) {
      final IncomingConnection connection = (IncomingConnection) o;

      log.fine("Accepted new connection from " + connection.remoteAddress());
      connection.handleWithAsyncHandler((Function<HttpRequest, Future<HttpResponse>>) httpRequest -> {
        final Future ask = (Future) Patterns.ask(context().actorOf(Props.create(RequestHandler.class, s3Client)), httpRequest, Timeout.apply(Duration.create(30, TimeUnit.SECONDS)));
        //noinspection unchecked
        return (Future<HttpResponse>)ask;
      }, materializer);
    }
    else unhandled(o);
  }

  public static void main(String[] args) {
    final ActorSystem system = ActorSystem.create("TBTS_Light_XMPP");
    system.actorOf(Props.create(ImageStorage.class));
  }

  private static class RequestHandler extends UntypedActor {
    private final AmazonS3Client s3Client;

    public RequestHandler(AmazonS3Client s3Client) {
      this.s3Client = s3Client;
    }

    @Override
    public void onReceive(Object o) throws Exception {
      HttpRequest request = (HttpRequest) o;
      Uri uri = request.getUri();
      final HttpResponse response;

      if (request.method() == HttpMethods.GET && uri.path().equals("/")) {
        response = HttpResponse.create().withEntity(
            MediaTypes.TEXT_HTML.toContentType(),
            "<html><body><form enctype=\"multipart/form-data\" action=\".\" method=\"post\" type=><input name=\"id\" type=\"text\"><input name=\"image\" accept=\"image/jpeg\" type=\"file\" alt=\"Submit\"><input type=\"submit\"></form></body></html>");
      }
      else if (request.method() == HttpMethods.GET) {
        final String id = uri.path().substring(1); // skip first slash
        S3Object image;
        try {
          image = s3Client.getObject(BUCKET_NAME, id);
        }
        catch(AmazonS3Exception e) {
          image = null;
        }
        if (image != null) {
          final String contentType = image.getObjectMetadata().getContentType();
          final byte[] contents = StreamTools.readByteStream(image.getObjectContent());
          final Option<MediaType> lookup = MediaTypes.lookup(contentType.split("/")[0], contentType.split("/")[1]);
          response = HttpResponse.create().withEntity(lookup.get().toContentType(), contents);
        }
        else
          response = HttpResponse.create().withStatus(404).withEntity("Page not found");
      }
      else if (request.method() == HttpMethods.POST) {
        final ActorMaterializer materializer = ActorMaterializer.create(getContext());
        final RequestEntity entity = request.entity();
        final MultipartMediaType mediaType = (MultipartMediaType) entity.contentType().mediaType();

        final PipedOutputStream output = new PipedOutputStream();
        final PipedInputStream input = new PipedInputStream(output, entity.getContentLengthOption().get().intValue());
        @SuppressWarnings("deprecation")
        final MultipartStream multipartStream =
            new MultipartStream(
                input,
                mediaType.params().get("boundary").get().getBytes());
        entity.getDataBytes().collect(new JavaPartialFunction<ByteString, MultipartStream>() {
          @Override
          public MultipartStream apply(ByteString param, boolean isCheck) throws Exception {
            if (isCheck)
              return null;
            final ByteBuffer copy = ByteBuffer.allocate(param.size());
            param.copyToBuffer(copy);
            output.write(copy.array());
            return multipartStream;
          }
        }).to(Sink.foreach((Procedure<MultipartStream>) param -> {
          boolean nextPart = multipartStream.skipPreamble();
          final ByteArrayOutputStream contents = new ByteArrayOutputStream();
          String id = null;
          String name = null;
          String mime = null;
          while (nextPart) {
            final String header = multipartStream.readHeaders();
            for(final String line : header.split("\n")) {
              if (line.startsWith("Content-Disposition: ")) {
                final ParameterParser parser = new ParameterParser();
                final Map<String, String> params = parser.parse(line.substring("Content-Disposition: ".length()), ';');
                if (params.containsKey("name"))
                  name = params.get("name");
              }
              else if (line.startsWith("Content-Type: ")) {
                mime = line.substring("Content-Type: ".length());
              }
            }
            if ("image".equals(name)) {
              multipartStream.readBodyData(contents);
            }
            else if ("id".equals(name)) {
              final ByteArrayOutputStream out = new ByteArrayOutputStream();
              multipartStream.readBodyData(out);
              id = new String(out.toByteArray(), StreamTools.UTF);
            }
            nextPart = multipartStream.readBoundary();
          }
          try {
            final File tempFile = File.createTempFile("asdasd", "adsasd");
            StreamTools.writeBytes(contents.toByteArray(), tempFile);
//            final String streamMD5 = new String(Base64.encodeBase64(DigestUtils.md5(img.contents)));
//            metadata.setContentMD5(streamMD5);

            final PutObjectRequest putRequest = new PutObjectRequest(BUCKET_NAME, id, tempFile);
            putRequest.setMetadata(new ObjectMetadata());
            assert mime != null;
            putRequest.getMetadata().setContentType(mime.trim());
            s3Client.putObject(putRequest);
            //noinspection ResultOfMethodCallIgnored
            tempFile.delete();
          }
          catch(Exception e) {
            e.printStackTrace();
          }
        })).run(materializer);
        response = HttpResponse.create().withEntity(
            MediaTypes.TEXT_HTML.toContentType(),
            "<html><body>Done</body></html>");
      }
      else response = HttpResponse.create().withStatus(404).withEntity("Page not found");

      getSender().tell(response, getSelf());
    }
  }
}
