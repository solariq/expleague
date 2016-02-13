package com.expleague.expert;

import com.expleague.expert.forms.Register;
import com.expleague.expert.profile.ProfileManager;
import com.expleague.expert.profile.UserProfile;
import com.expleague.expert.xmpp.ExpLeagueConnection;
import com.spbsu.commons.io.StreamTools;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
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

  public doSearch() {
  }

  @Override
  public void start(Stage stage) throws IOException {
    final Parent load = FXMLLoader.load(getClass().getResource("/forms/main.fxml"));
    Scene scene = new Scene(load, 1024, 800);
//    scene.getStylesheets().add(getClass().getResource("/Test.css").toExternalForm());
    stage.setScene(scene);
    stage.show();
    final UserProfile active = ProfileManager.instance().active();
    if (active == null)
      Register.register();
  }

  public static void main(String[] args) throws Exception {
    final Server server = new Server(8080);
    final QueuedThreadPool threadPool = new QueuedThreadPool(4);
    threadPool.setDaemon(true);
    server.setThreadPool(threadPool);

    server.addHandler(new AbstractHandler() {
      @Override
      public void handle(String s, HttpServletRequest request, HttpServletResponse response, int i) throws IOException, ServletException {
        System.out.println(StreamTools.readStream(request.getInputStream()));
        response.getOutputStream().close();
        response.setStatus(200);
      }
    });
    server.start();
    launch(args);
    ExpLeagueConnection.instance().disconnect();
  }
}
