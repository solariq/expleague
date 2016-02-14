package com.expleague.expert;

import com.expleague.expert.forms.Register;
import com.expleague.expert.forms.Vault;
import com.expleague.expert.profile.ProfileManager;
import com.expleague.expert.profile.UserProfile;
import com.expleague.expert.xmpp.ExpLeagueConnection;
import com.expleague.expert.xmpp.ExpertTask;
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
import java.util.logging.Logger;

/**
 * Created by solar on 03/02/16.
 */
public class doSearch extends Application {
  private static final Logger log = Logger.getLogger(doSearch.class.getName());
  private Vault vault;
  private Scene scene;

  public doSearch() {
  }

  @Override
  public void start(Stage stage) throws Exception {
    final Parent load = FXMLLoader.load(getClass().getResource("/forms/main.fxml"));
    scene = new Scene(load, 1024, 800);
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
        final Node lookup = scene.lookup("#vaultContainer");
        if (lookup != null)
          vault = (Vault) lookup.getUserData();


        final String item = StreamTools.readStream(request.getInputStream()).toString();
        response.setStatus(200);
        if (item.startsWith("{\"type\":\"pageVisited\"")) {
          response.setStatus(200);
          final UserProfile active = ProfileManager.instance().active();
          final ExpertTask task = active != null ? active.expert().task() : null;
          if (task != null)
            task.progress(item);
          else
            response.setStatus(503);
        }
        else if (vault != null && item.startsWith("{\"type\":\"newItem\"")) {
          final JsonNode json = mapper.readTree(item);
          vault.append(json.get("data"));
        }

        response.getOutputStream().close();
        System.out.println(item);
      }
    });
    server.start();
  }

  public static void main(String[] args) throws Exception {
    launch(args);
    ExpLeagueConnection.instance().disconnect();
  }
}
