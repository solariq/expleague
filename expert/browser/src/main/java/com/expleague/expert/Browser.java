package com.expleague.expert;

import com.expleague.expert.forms.Register;
import com.expleague.expert.profile.UserProfile;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by solar on 03/02/16.
 */
public class Browser extends Application {
  private static final Logger log = Logger.getLogger(Browser.class.getName());

  private UserProfile profile;

  public Browser() {
    try {
      profile = new UserProfile(new File(System.getenv("HOME") + "/.expleague"));
    } catch (IOException e) {
      log.log(Level.CONFIG, "Unable to create root directory for user profile!", e);
    }
  }

  @Override
  public void start(Stage stage) throws IOException {
    final Parent load = FXMLLoader.load(getClass().getResource("/forms/main.fxml"));
    Scene scene = new Scene(load, 1024, 800);
//    scene.getStylesheets().add(getClass().getResource("/Test.css").toExternalForm());
    stage.setScene(scene);
    stage.show();
    if (!profile.isRegistered()) {
      Register.register(stage);
    }
  }

  public static void main(String[] args) {
    launch(args);
  }
}
