package com.expleague.expert;

import com.expleague.expert.forms.Register;
import com.expleague.expert.forms.Vault;
import com.expleague.expert.profile.ProfileManager;
import com.expleague.expert.profile.UserProfile;
import com.expleague.expert.xmpp.ExpLeagueConnection;
import com.expleague.expert.xmpp.ExpertTask;
import com.expleague.model.patch.ImagePatch;
import com.expleague.model.patch.LinkPatch;
import com.expleague.model.patch.TextPatch;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spbsu.commons.io.StreamTools;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;
import org.mortbay.thread.QueuedThreadPool;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.logging.Logger;

/**
 * Created by solar on 03/02/16.
 */
public class doSearch extends Application {
  private static final Logger log = Logger.getLogger(doSearch.class.getName());

  public doSearch() {
  }

  @Override
  public void start(Stage stage) throws Exception {
    final Parent load = FXMLLoader.load(getClass().getResource("/forms/main.fxml"));
    final Scene scene = new Scene(load, 1024, 800);
//    scene.getStylesheets().add(getClass().getResource("/Test.css").toExternalForm());
    stage.setScene(scene);

    jettyStart();

    stage.show();
    final UserProfile active = ProfileManager.instance().active();
    if (active == null)
      Register.register();
  }

  private void jettyStart() throws Exception {
    final Server server = new Server(8080);
    final QueuedThreadPool threadPool = new QueuedThreadPool(4);
    threadPool.setDaemon(true);
    server.setThreadPool(threadPool);
    final ObjectMapper mapper = new ObjectMapper();

    server.addHandler(new AbstractHandler() {
      @Override
      public void handle(String s, HttpServletRequest request, HttpServletResponse response, int i) throws IOException, ServletException {
        final String item = StreamTools.readStream(request.getInputStream()).toString();
        response.setStatus(200);
        System.out.println(item);
        final UserProfile active = ProfileManager.instance().active();
        final ExpertTask task = active != null ? active.expert().task() : null;
        if (item.startsWith("{\"type\":\"pageVisited\"")) {
          if (task != null)
            task.progress(item);
          else
            response.setStatus(503);
        }
        else if (task != null && item.startsWith("{\"type\":\"newItem\"")) {
          final JsonNode data = mapper.readTree(item).get("data");
          if (data.has("text")) {
            final JsonNode jsonText = data.get("text");
            task.patchesProperty().add(new TextPatch(
                jsonText.get("referer").asText(),
                jsonText.has("title") ? jsonText.get("title").asText() : "",
                jsonText.get("text").asText())
            );
          }
          if (data.has("image")) {
            final JsonNode jsonImage = data.get("image");
            task.patchesProperty().add(new ImagePatch(
                jsonImage.get("referer").asText(),
                jsonImage.has("title") ? jsonImage.get("title").asText() : "",
                jsonImage.get("image").asText())
            );
          }
          if (data.has("link")) {
            final JsonNode jsonImage = data.get("link");
            task.patchesProperty().add(new LinkPatch(
                jsonImage.get("referer").asText(),
                jsonImage.has("title") ? jsonImage.get("title").asText() : "",
                jsonImage.get("href").asText())
            );
          }
        }

        response.getOutputStream().close();
      }
    });
    server.start();
  }

  public static void main(String[] args) throws Exception {
    launch(args);
    ExpLeagueConnection.instance().disconnect();
  }
}
